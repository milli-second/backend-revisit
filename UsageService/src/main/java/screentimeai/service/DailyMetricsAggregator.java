package screentimeai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import screentimeai.domain.RawData;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * RawData를 받아서 AppUsageTracker로 위임하는 Aggregator
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DailyMetricsAggregator {

    private final AppUsageTracker appUsageTracker;

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
}
