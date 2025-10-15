package screentimeai.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import screentimeai.service.DailyMetricsAggregator;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/usage/stats")
@RequiredArgsConstructor
@Slf4j
public class UsageStatsController {

    private final DailyMetricsAggregator metricsAggregator;

    /**
     * 실시간 통계 조회 API (현재 시간 기준 24시간)
     */
    @GetMapping("/realtime/{subjectId}")
    public ResponseEntity<Map<Object, Object>> getRealtimeStats(@PathVariable UUID subjectId) {
        log.info("Fetching realtime stats for subjectId: {}", subjectId);

        Map<Object, Object> stats = metricsAggregator.getRealtimeStats(subjectId);

        if (stats.isEmpty()) {
            log.warn("No realtime stats found for subjectId: {}", subjectId);
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(stats);
    }
}
