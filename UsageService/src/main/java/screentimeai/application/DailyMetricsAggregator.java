package screentimeai.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import screentimeai.domain.entity.DailyUserMetrics;
import screentimeai.domain.entity.RawData;
import screentimeai.infra.repository.DailyUserMetricsRepository;
import screentimeai.infra.repository.RawDataRepository;
import screentimeai.presentation.dto.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * RawData를 받아서 AppUsageTracker로 위임하는 Aggregator
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DailyMetricsAggregator {

    private final AppUsageTracker appUsageTracker;
    private final DailyUserMetricsRepository dailyUserMetricsRepository;
    private final RawDataRepository rawDataRepository;

    /**
     * raw_data 리스트를 받아서 Redis에 실시간으로 집계
     */
    public void aggregateRawDataBatch(List<RawData> rawDataList) {
        log.debug("Aggregating {} events to Redis", rawDataList.size());
        try {
            for (RawData rawData : rawDataList) {
                aggregateRawData(rawData);
            }
            log.debug("Batch aggregation completed for {} events", rawDataList.size());
        } catch (Exception e) {
            log.error("Error aggregating batch to Redis", e);
            throw e;
        }
    }

    /**
     * raw_data를 받아서 Redis에 실시간으로 집계
     */
    public void aggregateRawData(RawData rawData) {
        log.debug("Aggregating event: subjectId={}, type={}, package={}",
                rawData.getSubjectId(), rawData.getType(), rawData.getPackageName());

        try {
            appUsageTracker.processEvent(rawData);
        } catch (Exception e) {
            log.error("Failed to aggregate event for subject: {}", rawData.getSubjectId(), e);
            throw e;
        }
    }

    /**
     * 실시간 통계 조회 (API용)
     */
    public Map<Object, Object> getRealtimeStats(UUID subjectId) {
        return appUsageTracker.getRealtimeStats(subjectId);
    }

    /**
     * 7일치 사용 패턴 조회 (오늘 기준 어제부터 7일치)
     * @param subjectId 사용자 ID
     * @return 각 요일별 총 사용 시간 리스트 (최신순 정렬)
     */
    public List<DailyUsageResponse> getWeeklyUsagePattern(UUID subjectId) {
        LocalDate today = LocalDate.now();
        LocalDate endDate = today.minusDays(1);  // 어제
        LocalDate startDate = endDate.minusDays(6);  // 어제부터 7일 전

        log.info("Fetching weekly usage pattern for subjectId: {} from {} to {}",
                subjectId, startDate, endDate);

        List<DailyUserMetrics> metrics = dailyUserMetricsRepository
                .findBySubjectIdAndSummaryDateBetweenOrderBySummaryDateDesc(subjectId, startDate, endDate);

        log.debug("Found {} daily metrics for subjectId: {}", metrics.size(), subjectId);

        return metrics.stream()
                .map(metric -> DailyUsageResponse.builder()
                        .date(metric.getSummaryDate())
                        .totalUsageMinutes(metric.getTotalUsageMinutes())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 24시간 카테고리 사용 비율 조회 (상위 4개 + 기타)
     * @param subjectId 사용자 ID
     * @return 카테고리별 사용 비율 리스트 (사용량 내림차순)
     */
    public List<CategoryUsageResponse> getCategoryUsage(UUID subjectId) {
        log.info("Fetching category usage for subjectId: {}", subjectId);
        return appUsageTracker.getCategoryUsage(subjectId);
    }

    public List<TopApp> getTop3Apps(UUID subjectId) {
        return appUsageTracker.getTop3Apps(subjectId);
    }

    public ThreeDayUsageResponse getThreeDayUsage(UUID subjectId) {
        // 사용자의 최근 raw_data에서 UTC offset 가져오기
        RawData latestRawData = rawDataRepository.findFirstBySubjectIdOrderByTimestampDesc(subjectId);
        int utcOffset = (latestRawData != null && latestRawData.getUtcoffset() != null)
                ? latestRawData.getUtcoffset() : 9; // 기본값: 한국 시간 (UTC+9)

        log.info("Using UTC offset {} for subjectId: {}", utcOffset, subjectId);

        TodayUsageSummary todaySummary = appUsageTracker.getTodayUsage(subjectId, utcOffset);

        // UTC offset을 고려한 오늘 날짜 계산
        java.time.ZoneId zoneId = java.time.ZoneId.ofOffset("UTC", java.time.ZoneOffset.ofHours(utcOffset));
        LocalDate today = java.time.LocalDate.now(zoneId);
        LocalDate yesterday = today.minusDays(1);
        LocalDate dayBeforeYesterday = today.minusDays(2);

        List<DailyUserMetrics> pastMetrics = dailyUserMetricsRepository
                .findBySubjectIdAndSummaryDateBetweenOrderBySummaryDateDesc(subjectId, dayBeforeYesterday, yesterday);

        List<DailyUsageSummary> dailySummaries = new java.util.ArrayList<>();
        dailySummaries.add(todaySummary.getDailySummary());

        for (DailyUserMetrics metric : pastMetrics) {
            dailySummaries.add(DailyUsageSummary.builder()
                    .date(metric.getSummaryDate())
                    .dawnMinutes(metric.getDawnMinutes())
                    .morningMinutes(metric.getMorningMinutes())
                    .afternoonMinutes(metric.getAfternoonMinutes())
                    .eveningMinutes(metric.getEveningMinutes())
                    .build());
        }

        dailySummaries.sort(java.util.Comparator.comparing(DailyUsageSummary::getDate).reversed());

        return ThreeDayUsageResponse.builder()
                .dailySummaries(dailySummaries)
                .mostActiveHourStart(todaySummary.getMostActiveHourStart())
                .build();
    }
}
