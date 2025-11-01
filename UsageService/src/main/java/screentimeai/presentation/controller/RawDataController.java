package screentimeai.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import screentimeai.domain.entity.RawData;
import screentimeai.infra.repository.RawDataRepository;
import screentimeai.presentation.dto.RawDataRequest;
import screentimeai.application.DailyMetricsAggregator;
import screentimeai.application.DailyMetricsBatchService;

import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Tag(name = "Raw Data", description = "원본 사용 데이터 수집 및 조회 API")
@RestController
@RequestMapping("/usage/raw-data")
@RequiredArgsConstructor
@Slf4j
public class RawDataController {

    private final RawDataRepository rawDataRepository;
    private final DailyMetricsAggregator metricsAggregator;
    private final DailyMetricsBatchService batchService;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * RawData 저장 API (단건 또는 List 모두 처리)
     */
    @Operation(
        summary = "원본 사용 데이터 저장",
        description = "사용자의 앱/웹사이트 사용 기록을 수집하여 저장합니다. Redis를 통한 멱등성 처리로 중복 방지하며, 실시간 집계 및 TimescaleDB에 이력을 저장합니다."
    )
    @SecurityRequirement(name = "JWT Authentication")
    @PostMapping("/test-account")
    public ResponseEntity<String> createRawDataTestUser(
            @Parameter(description = "사용자 ID (JWT에서 자동 추출)", hidden = true)
            @RequestHeader("X-User-Id") String userId) {
        UUID subjectId = UUID.fromString(userId);
        log.info("▶ [createRawData/test] API called for userId={}", userId);

        List<RawData> newDataList = new ArrayList<>();

        try {
            // 테스트 계정은 오늘 자정부터 현재까지 데이터만 조회 (배치는 어제까지 처리했으므로)
            Instant now = Instant.now();
            // 사용자의 UTC offset 가져오기 (기본값: 9 - 한국 시간)
            RawData latestRawData = rawDataRepository.findFirstBySubjectIdOrderByTimestampDesc(subjectId);
            int utcOffset = (latestRawData != null && latestRawData.getUtcoffset() != null)
                    ? latestRawData.getUtcoffset() : 9;

            // 오늘 자정 계산 (사용자의 UTC offset 기준)
            ZoneId zoneId = ZoneId.ofOffset("UTC", java.time.ZoneOffset.ofHours(utcOffset));
            Instant todayMidnight = java.time.LocalDate.now(zoneId).atStartOfDay(zoneId).toInstant();

            List<RawData> recentData = rawDataRepository.findBySubjectIdAndTimestampBetween(subjectId, todayMidnight, now);
            log.info("📊 DB fetch complete — found {} records for today (midnight={}, now={})", recentData.size(), todayMidnight, now);

            if (recentData.isEmpty()) {
                log.warn("⚠ No recent data found for subjectId={}", subjectId);
                return ResponseEntity.badRequest().body("No recent data found for subjectId=" + subjectId);
            }

            newDataList.addAll(recentData);

            // 테스트 계정은 시뮬레이션 목적이므로 기존 오늘 데이터를 삭제하고 새로 시작
            log.info("🔄 Test account: Clearing today's Redis data before simulation");

            // 오늘 날짜의 Redis 키 삭제 (daily_metrics와 realtime_metrics)
            java.time.LocalDate today = java.time.LocalDate.now(zoneId);
            String dailyKey = "daily_metrics:" + subjectId + ":" + today;
            String realtimeEventsKey = "realtime_metrics:" + subjectId + ":events";
            String realtimeStatsKey = "realtime_metrics:" + subjectId + ":stats";

            redisTemplate.delete(dailyKey);
            redisTemplate.delete(realtimeEventsKey);
            redisTemplate.delete(realtimeStatsKey);
            log.info("🗑️ Deleted existing Redis keys for today: {}", dailyKey);

            // 멱등성 키도 삭제 (processed:subjectId:timestamp)
            String processedKeyPattern = "processed:" + subjectId + ":*";
            Set<String> processedKeys = redisTemplate.keys(processedKeyPattern);
            if (processedKeys != null && !processedKeys.isEmpty()) {
                redisTemplate.delete(processedKeys);
                log.info("🗑️ Deleted {} processed keys for clean simulation", processedKeys.size());
            }

            log.info("🚀 Aggregating {} records into Redis via metricsAggregator...", newDataList.size());
            metricsAggregator.aggregateRawDataBatch(newDataList);
            log.info("🎯 Aggregation complete!");

            String resultMsg = String.format("Processed %d records (test mode: no deduplication)",
                    newDataList.size());
            log.info("✅ Finished processing: {}", resultMsg);

            return ResponseEntity.ok(resultMsg);

        } catch (Exception e) {
            log.error("💥 Exception occurred in createRawData/test", e);
            return ResponseEntity.internalServerError().body("Error occurred: " + e.getMessage());
        }
    }



    @PostMapping
    public ResponseEntity<String> createRawData(
            @Parameter(description = "저장할 원본 데이터 리스트", required = true)
            @RequestBody List<RawDataRequest> rawDataRequestList) {
        log.info("Received {} raw data records", rawDataRequestList.size());

        // DTO를 Entity로 변환
        List<RawData> rawDataList = rawDataRequestList.stream()
                .map(RawDataRequest::toEntity)
                .collect(Collectors.toList());

        // Redis 멱등성 키로 중복 체크
        List<RawData> newDataList = new ArrayList<>();
        int duplicateCount = 0;

        for (RawData rawData : rawDataList) {
            // 멱등성 키: processed:{subjectId}:{timestamp_millis}
            String idempotencyKey = String.format("processed:%s:%d",
                    rawData.getSubjectId(),
                    rawData.getTimestamp().toEpochMilli());

            // Redis에 키가 없으면 설정하고 true 반환 (3일 보관)
            Boolean isNew = redisTemplate.opsForValue()
                    .setIfAbsent(idempotencyKey, "1", Duration.ofDays(3));

            if (Boolean.TRUE.equals(isNew)) {
                newDataList.add(rawData);
            } else {
                duplicateCount++;
                log.debug("Duplicate event skipped: subjectId={}, timestamp={}",
                        rawData.getSubjectId(), rawData.getTimestamp());
            }
        }

        // 신규 데이터를 날짜 기준으로 분리 (오늘 0시 기준)
        int processedCount = 0;
        int historicalCount = 0;
        int realtimeCount = 0;

        if (!newDataList.isEmpty()) {
            // 1. 오늘 날짜 데이터와 과거 날짜 데이터 분리
            List<RawData> realtimeData = new ArrayList<>();
            List<RawData> historicalData = new ArrayList<>();

            for (RawData rawData : newDataList) {
                int utcOffset = rawData.getUtcoffset() != null ? rawData.getUtcoffset() : 9;
                java.time.LocalDate dataDate = getLocalDate(rawData.getTimestamp(), utcOffset);
                java.time.LocalDate today = java.time.LocalDate.now(
                        java.time.ZoneId.ofOffset("UTC", java.time.ZoneOffset.ofHours(utcOffset)));

                ZoneOffset offset = ZoneOffset.ofHours(utcOffset);
                OffsetDateTime dataTime = rawData.getTimestamp().atOffset(offset);
                OffsetDateTime cutoff = OffsetDateTime.now(offset).minusHours(24);

                if (dataTime.isAfter(cutoff)) {
                    realtimeData.add(rawData);
                }

                if (!dataDate.equals(today)) {
                    historicalData.add(rawData);
                }
            }

            // 2. 실시간 데이터 → Redis 집계 (24시간 슬라이딩 윈도우)
            if (!realtimeData.isEmpty()) {
                metricsAggregator.aggregateRawDataBatch(realtimeData);
                realtimeCount = realtimeData.size();
                log.info("Aggregated {} realtime events to Redis", realtimeCount);
            }

            // 3. 과거 데이터 → 즉시 배치 집계하여 DB 저장
            if (!historicalData.isEmpty()) {
                batchService.processHistoricalDataBatch(historicalData);
                historicalCount = historicalData.size();
                log.info("Processed {} historical events immediately", historicalCount);
            }

            // 4. TimescaleDB에 원본 데이터 저장 (이력 관리용)
            rawDataRepository.saveAll(newDataList);

            processedCount = newDataList.size();
        }

        log.info("Processed {} new events ({} realtime, {} historical, {} duplicates skipped)",
                processedCount, realtimeCount, historicalCount, duplicateCount);

        return ResponseEntity.ok(String.format(
                "Processed %d out of %d events (%d realtime to Redis, %d historical to DB, %d duplicates skipped)",
                processedCount, rawDataList.size(), realtimeCount, historicalCount, duplicateCount));
    }

    /**
     * 특정 사용자의 RawData 조회 (테스트용)
     */
    @Operation(
        summary = "특정 사용자 원본 데이터 조회(개발용)",
        description = "특정 사용자(subject_id)의 원본 사용 데이터를 조회합니다."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = @Content(schema = @Schema(implementation = RawData.class))
        ),
        @ApiResponse(responseCode = "404", description = "데이터를 찾을 수 없음")
    })

    @GetMapping("/by-subject/{subjectId}")
    public ResponseEntity<List<RawData>> getRawDataBySubjectId(
            @Parameter(description = "사용자 ID (UUID)", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID subjectId) {
        List<RawData> data = rawDataRepository.findBySubjectId(subjectId);

        if (data.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(data);
    }

    /**
     * UTC offset을 고려한 LocalDate 계산
     */
    private java.time.LocalDate getLocalDate(Instant timestamp, int utcOffsetHours) {
        ZoneId zoneId = ZoneId.ofOffset("UTC", java.time.ZoneOffset.ofHours(utcOffsetHours));
        return timestamp.atZone(zoneId).toLocalDate();
    }
}
