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
    @SecurityRequirement(name = "JWT Authentication")
    @GetMapping("/top3")
    public ResponseEntity<List<TopApp>> getTop3Apps(
            @Parameter(description = "사용자 ID (JWT에서 자동 추출)", hidden = true)
            @RequestHeader("X-User-Id") String userId) {
        UUID subjectId = UUID.fromString(userId);
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
    @SecurityRequirement(name = "JWT Authentication")
    @GetMapping("/weekly")
    public ResponseEntity<List<DailyUsageResponse>> getWeeklyUsagePattern(
            @Parameter(description = "사용자 ID (JWT에서 자동 추출)", hidden = true)
            @RequestHeader("X-User-Id") String userId) {
        UUID subjectId = UUID.fromString(userId);
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
    @SecurityRequirement(name = "JWT Authentication")
    @GetMapping("/category")
    public ResponseEntity<List<CategoryUsageResponse>> getCategoryUsage(
            @Parameter(description = "사용자 ID (JWT에서 자동 추출)", hidden = true)
            @RequestHeader("X-User-Id") String userId) {
        UUID subjectId = UUID.fromString(userId);
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
    @SecurityRequirement(name = "JWT Authentication")
    @GetMapping("/3day")
    public ResponseEntity<ThreeDayUsageResponse> getThreeDayUsage(
            @Parameter(description = "사용자 ID (JWT에서 자동 추출)", hidden = true)
            @RequestHeader("X-User-Id") String userId) {
        UUID subjectId = UUID.fromString(userId);
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
    @SecurityRequirement(name = "JWT Authentication")
    @GetMapping("/weekly-trend")
    public ResponseEntity<WeeklyTrendResponse> getWeeklyTrend(
            @Parameter(description = "사용자 ID (JWT에서 자동 추출)", hidden = true)
            @RequestHeader("X-User-Id") String userId) {
        UUID subjectId = UUID.fromString(userId);
        log.info("Fetching weekly trend for subjectId: {}", subjectId);

        WeeklyTrendResponse weeklyTrend = weeklyTrendService.getWeeklyTrend(subjectId);

        if (weeklyTrend.getTop3Categories().isEmpty() &&
                weeklyTrend.getTotalUsageChange().getYesterdayMinutes() == 0) {
            log.warn("No weekly trend data found for subjectId: {}", subjectId);
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(weeklyTrend);
    }

    /**
     * 오늘 사용시간 조회 API (자정부터 현재까지)
     */
    @Operation(
        summary = "오늘 사용시간 조회",
        description = "특정 사용자의 오늘(자정부터 현재까지) 사용 통계를 Redis에서 조회합니다. " +
                "날짜, 총 사용시간(분), 픽업 카운트를 배열 형태로 반환합니다."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "오늘 사용시간 조회 성공",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = screentimeai.presentation.dto.TodayUsageResponse.class))),
        @ApiResponse(responseCode = "404", description = "오늘 사용 기록 없음")
    })
    @SecurityRequirement(name = "JWT Authentication")
    @GetMapping("/today")
    public ResponseEntity<List<screentimeai.presentation.dto.TodayUsageResponse>> getTodayUsage(
            @Parameter(description = "사용자 ID (JWT에서 자동 추출)", hidden = true)
            @RequestHeader("X-User-Id") String userId) {
        UUID subjectId = UUID.fromString(userId);
        log.info("Fetching today usage for subjectId: {}", subjectId);

        List<screentimeai.presentation.dto.TodayUsageResponse> todayUsage = metricsAggregator.getTodayUsage(subjectId);

        if (todayUsage.isEmpty() || todayUsage.get(0).getTotalUsageMinutes() == null) {
            log.warn("No today usage found for subjectId: {}", subjectId);
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(todayUsage);
    }
}
