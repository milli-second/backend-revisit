package screentimeai.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import screentimeai.dto.CategoryUsageResponse;
import screentimeai.dto.DailyUsageResponse;
import screentimeai.service.DailyMetricsAggregator;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Usage Statistics", description = "사용 통계 조회 API")
@RestController
@RequestMapping("/usage/stats")
@RequiredArgsConstructor
@Slf4j
public class UsageStatsController {

    private final DailyMetricsAggregator metricsAggregator;

    /**
     * 실시간 통계 조회 API (현재 시간 기준 24시간)
     */
    @Operation(
        summary = "실시간 사용 통계 조회",
        description = "특정 사용자의 현재 시간 기준 24시간 실시간 사용 통계를 Redis에서 조회합니다."
    )
    @GetMapping("/realtime/{subjectId}")
    public ResponseEntity<Map<Object, Object>> getRealtimeStats(
            @Parameter(description = "사용자 ID (UUID)", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID subjectId) {
        log.info("Fetching realtime stats for subjectId: {}", subjectId);

        Map<Object, Object> stats = metricsAggregator.getRealtimeStats(subjectId);

        if (stats.isEmpty()) {
            log.warn("No realtime stats found for subjectId: {}", subjectId);
            return ResponseEntity.notFound().build();
        }


        return ResponseEntity.ok(stats);
    }

    /**
     * 7일치 사용 패턴 조회 API (오늘 기준 어제부터 7일치)
     */
    @Operation(
        summary = "7일치 사용 패턴 조회",
        description = "특정 사용자의 최근 7일간(어제부터 7일치) 일별 총 사용 시간을 DB에서 조회합니다."
    )
    @GetMapping("/weekly/{subjectId}")
    public ResponseEntity<List<DailyUsageResponse>> getWeeklyUsagePattern(
            @Parameter(description = "사용자 ID (UUID)", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID subjectId) {
        log.info("Fetching weekly usage pattern for subjectId: {}", subjectId);

        List<DailyUsageResponse> weeklyPattern = metricsAggregator.getWeeklyUsagePattern(subjectId);

        if (weeklyPattern.isEmpty()) {
            log.warn("No weekly pattern found for subjectId: {}", subjectId);
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(weeklyPattern);
    }

    /**
     * 24시간 카테고리 사용 비율 조회 API (상위 4개 + 기타)
     */
    @Operation(
        summary = "24시간 카테고리 사용 비율 조회",
        description = "특정 사용자의 현재 시간 기준 24시간 카테고리별 사용 비율을 Redis에서 조회합니다. 상위 4개 카테고리는 개별로 표시하고, 나머지는 '기타(OTHERS)'로 묶어서 반환합니다."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "카테고리 사용 비율 조회 성공",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = CategoryUsageResponse.class))),
        @ApiResponse(responseCode = "404", description = "24시간 내 사용 기록 없음")
    })
    @GetMapping("/category/{subjectId}")
    public ResponseEntity<List<CategoryUsageResponse>> getCategoryUsage(
            @Parameter(description = "사용자 ID (UUID)", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID subjectId) {
        log.info("Fetching category usage for subjectId: {}", subjectId);

        List<CategoryUsageResponse> categoryUsage = metricsAggregator.getCategoryUsage(subjectId);

        if (categoryUsage.isEmpty()) {
            log.warn("No category usage found for subjectId: {}", subjectId);
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(categoryUsage);
    }
}
