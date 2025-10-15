package screentimeai.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import screentimeai.domain.RawData;
import screentimeai.domain.RawDataRepository;
import screentimeai.dto.RawDataRequest;
import screentimeai.service.DailyMetricsAggregator;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/usage/raw-data")
@RequiredArgsConstructor
@Slf4j
public class RawDataController {

    private final RawDataRepository rawDataRepository;
    private final DailyMetricsAggregator metricsAggregator;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * RawData 저장 API (단건 또는 List 모두 처리)
     */
    @PostMapping
    public ResponseEntity<String> createRawData(@RequestBody List<RawDataRequest> rawDataRequestList) {
        log.info("Received {} raw data records", rawDataRequestList.size());

        // DTO를 Entity로 변환
        List<RawData> rawDataList = rawDataRequestList.stream()
                .map(RawDataRequest::toEntity)
                .collect(Collectors.toList());

        // Redis 멱등성 키로 중복 체크 및 집계
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


        // 신규 데이터만 Redis 집계 및 DB 저장
        int processedCount = 0;
        if (!newDataList.isEmpty()) {
            // Redis 실시간 집계
            metricsAggregator.aggregateRawDataBatch(newDataList);

            // TimescaleDB에 원본 데이터 저장 (이력 관리용)
            rawDataRepository.saveAll(newDataList);

            processedCount = newDataList.size();
        }

        log.info("Processed {} new events ({} duplicates skipped)",
                processedCount, duplicateCount);

        return ResponseEntity.ok(String.format(
                "Processed %d out of %d events (aggregated to Redis and saved to DB)",
                processedCount, rawDataList.size()));
    }

    /**
     * 전체 RawData 조회 (테스트용)
     */
    @GetMapping
    public ResponseEntity<List<RawData>> getAllRawData() {
        return ResponseEntity.ok(rawDataRepository.findAll());
    }
}
