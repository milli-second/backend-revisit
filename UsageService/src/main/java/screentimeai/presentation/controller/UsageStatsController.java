package screentimeai.presentation.controller;

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
import screentimeai.presentation.dto.CategoryUsageResponse;
import screentimeai.presentation.dto.DailyUsageResponse;
import screentimeai.application.DailyMetricsAggregator;
import screentimeai.presentation.dto.ThreeDayUsageResponse;
import screentimeai.presentation.dto.TopApp;
import screentimeai.domain.dto.WeeklyTrendResponse;
import screentimeai.domain.service.WeeklyTrendService;

import java.util.List;
import java.util.UUID;

@Tag(name = "Usage Statistics", description = "사용 통계 조회 API")
@RestController
@RequestMapping("/usage/stats")
@RequiredArgsConstructor
@Slf4j
public class UsageStatsController {

    private final DailyMetricsAggregator metricsAggregator;
    private final WeeklyTrendService weeklyTrendService;

    /**
     * 실시간 통계 조회 API (현재 시간 기준 24시간)
     */
    @Operation(
        summary = "실시간 Top 3 앱 조회",
        description = "특정 사용자의 현재 시간 기준 24시간 실시간 Top 3 앱 사용 통계를 Redis에서 조회합니다."
    )
    @GetMapping("/top3/{subjectId}")
    public ResponseEntity<List<TopApp>> getTop3Apps(
            @Parameter(description = "사용자 ID (UUID)", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID subjectId) {
        log.info("Fetching top 3 apps for subjectId: {}", subjectId);

        List<TopApp> topApps = metricsAggregator.getTop3Apps(subjectId);

        if (topApps.isEmpty()) {
            log.warn("No top 3 apps found for subjectId: {}", subjectId);
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(topApps);
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

    @Operation(
        summary = "3일치 시간대별 사용 통계 조회",
        description = "어제, 그제는 DB, 오늘은 Redis에서 데이터를 가져와 시간대별 사용량을 반환합니다. 오늘 데이터 기준 가장 활발한 2시간의 시작 시간도 함께 반환합니다."
    )
    @GetMapping("/3day/{subjectId}")
    public ResponseEntity<ThreeDayUsageResponse> getThreeDayUsage(
            @Parameter(description = "사용자 ID (UUID)", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID subjectId) {
        log.info("Fetching 3-day usage for subjectId: {}", subjectId);

        ThreeDayUsageResponse threeDayUsage = metricsAggregator.getThreeDayUsage(subjectId);

        if (threeDayUsage.getDailySummaries().isEmpty()) {
            log.warn("No 3-day usage found for subjectId: {}", subjectId);
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(threeDayUsage);
    }

    /**
     * 주간 변화 트렌드 조회 API
     * - 어제(D-1) vs 최근 7일(D-2~D-8) 평균 비교
     * - 총 사용시간 변화 + 카테고리별 변화 TOP 3 (절대값 기준)
     */
    @Operation(
        summary = "주간 변화 트렌드 조회",
        description = "어제와 최근 7일 평균을 비교한 주간 변화 트렌드를 조회합니다. " +
                "총 사용시간 변화와 카테고리별 변화율이 큰 TOP 3를 반환합니다. " +
                "카테고리는 원본 Google Play 카테고리(GAME, SOCIAL, ART_AND_DESIGN 등)입니다."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "주간 변화 트렌드 조회 성공",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = WeeklyTrendResponse.class))),
        @ApiResponse(responseCode = "404", description = "데이터 없음")
    })
    @GetMapping("/weekly-trend/{subjectId}")
    public ResponseEntity<WeeklyTrendResponse> getWeeklyTrend(
            @Parameter(description = "사용자 ID (UUID)", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID subjectId) {
        log.info("Fetching weekly trend for subjectId: {}", subjectId);

        WeeklyTrendResponse weeklyTrend = weeklyTrendService.getWeeklyTrend(subjectId);

        if (weeklyTrend.getTop3Categories().isEmpty() &&
                weeklyTrend.getTotalUsageChange().getYesterdayMinutes() == 0) {
            log.warn("No weekly trend data found for subjectId: {}", subjectId);
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(weeklyTrend);
    }
}
