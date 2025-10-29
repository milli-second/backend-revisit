package screentimeai.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import screentimeai.domain.dto.WeeklyTrendResponse;
import screentimeai.domain.entity.DailyCategoryUsage;
import screentimeai.domain.entity.DailyUserMetrics;
import screentimeai.infra.repository.DailyCategoryUsageRepository;
import screentimeai.infra.repository.DailyUserMetricsRepository;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 주간 변화 트렌드 계산 서비스
 * - 어제 사용량 vs 최근 7일(D-1 ~ D-7) 평균 비교
 * - 변화율 큰 카테고리 TOP 3 추출 (절대값 기준)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WeeklyTrendService {

    private final DailyCategoryUsageRepository categoryUsageRepository;
    private final DailyUserMetricsRepository metricsRepository;

    /**
     * 주간 변화 트렌드 조회
     * @param subjectId 사용자 ID
     * @return 주간 변화 트렌드 (총 사용시간 + 카테고리 TOP 3)
     */
    public WeeklyTrendResponse getWeeklyTrend(UUID subjectId) {
        LocalDate yesterday = LocalDate.now().minusDays(1);  // D-1
        LocalDate twoDaysAgo = LocalDate.now().minusDays(2); // D-2
        LocalDate eightDaysAgo = twoDaysAgo.minusDays(6);    // D-8

        log.info("Calculating weekly trend for subjectId: {}, yesterday: {}, average period: {} to {}",
                subjectId, yesterday, eightDaysAgo, twoDaysAgo);

        // 1. 총 사용시간 변화 계산
        WeeklyTrendResponse.TotalUsageChange totalUsageChange =
                calculateTotalUsageChange(subjectId, yesterday, eightDaysAgo, twoDaysAgo);

        // 2. 카테고리별 변화 TOP 3 계산
        List<WeeklyTrendResponse.CategoryTrend> top3Categories =
                calculateTop3CategoryTrends(subjectId, yesterday, eightDaysAgo, twoDaysAgo);

        return WeeklyTrendResponse.builder()
                .baseDate(yesterday)
                .totalUsageChange(totalUsageChange)
                .top3Categories(top3Categories)
                .build();
    }

    /**
     * 총 사용시간 변화 계산
     * @param yesterday 어제 (D-1)
     * @param eightDaysAgo 8일 전 (D-8)
     * @param twoDaysAgo 2일 전 (D-2)
     */
    private WeeklyTrendResponse.TotalUsageChange calculateTotalUsageChange(
            UUID subjectId, LocalDate yesterday, LocalDate eightDaysAgo, LocalDate twoDaysAgo) {

        // 어제 사용시간
        Optional<DailyUserMetrics> yesterdayMetrics =
                metricsRepository.findBySubjectIdAndSummaryDate(subjectId, yesterday);

        if (yesterdayMetrics.isEmpty()) {
            log.warn("No metrics found for yesterday: {}", yesterday);
            return buildEmptyTotalUsageChange();
        }

        int yesterdayMinutes = yesterdayMetrics.get().getTotalUsageMinutes();

        // 최근 7일 평균 (D-2 ~ D-8, 어제 제외)
        List<DailyUserMetrics> weeklyMetrics = metricsRepository
                .findBySubjectIdAndSummaryDateBetweenOrderBySummaryDateDesc(
                        subjectId, eightDaysAgo, twoDaysAgo);

        if (weeklyMetrics.isEmpty()) {
            log.warn("No weekly metrics found for period: {} to {}", eightDaysAgo, twoDaysAgo);
            return buildEmptyTotalUsageChange();
        }

        // 7일 평균 계산
        double weeklyAverage = weeklyMetrics.stream()
                .mapToInt(DailyUserMetrics::getTotalUsageMinutes)
                .average()
                .orElse(0.0);

        // 변화량 및 변화율 계산
        double changeMinutes = yesterdayMinutes - weeklyAverage;
        double changeRate = weeklyAverage > 0 ? (changeMinutes / weeklyAverage) * 100 : 0.0;

        return WeeklyTrendResponse.TotalUsageChange.builder()
                .yesterdayMinutes(yesterdayMinutes)
                .weeklyAverageMinutes(weeklyAverage)
                .changeMinutes(changeMinutes)
                .changeRate(changeRate)
                .build();
    }

    /**
     * 카테고리별 변화 TOP 3 계산 (절대값 기준)
     * @param yesterday 어제 (D-1)
     * @param eightDaysAgo 8일 전 (D-8)
     * @param twoDaysAgo 2일 전 (D-2)
     */
    private List<WeeklyTrendResponse.CategoryTrend> calculateTop3CategoryTrends(
            UUID subjectId, LocalDate yesterday, LocalDate eightDaysAgo, LocalDate twoDaysAgo) {

        // 어제 카테고리별 사용량
        List<DailyCategoryUsage> yesterdayUsages =
                categoryUsageRepository.findBySubjectIdAndSummaryDate(subjectId, yesterday);

        if (yesterdayUsages.isEmpty()) {
            log.warn("No category usage found for yesterday: {}", yesterday);
            return Collections.emptyList();
        }

        // 최근 7일 카테고리별 사용량 (D-2 ~ D-8, 어제 제외)
        List<DailyCategoryUsage> weeklyUsages =
                categoryUsageRepository.findBySubjectIdAndSummaryDateBetween(
                        subjectId, eightDaysAgo, twoDaysAgo);

        if (weeklyUsages.isEmpty()) {
            log.warn("No weekly category usage found for period: {} to {}", eightDaysAgo, twoDaysAgo);
            return Collections.emptyList();
        }

        // 카테고리별 평균 계산
        Map<String, Double> categoryAverages = calculateCategoryAverages(weeklyUsages);

        // 어제 사용량과 비교하여 변화율 계산
        List<WeeklyTrendResponse.CategoryTrend> trends = new ArrayList<>();

        for (DailyCategoryUsage yesterdayUsage : yesterdayUsages) {
            String categoryCode = yesterdayUsage.getCategoryCode();
            int yesterdayMinutes = yesterdayUsage.getUsageMinutes();
            Double weeklyAverage = categoryAverages.getOrDefault(categoryCode, 0.0);

            // 변화량 및 변화율 계산
            double changeMinutes = yesterdayMinutes - weeklyAverage;
            double changeRate = weeklyAverage > 0 ? (changeMinutes / weeklyAverage) * 100 : 0.0;

            WeeklyTrendResponse.CategoryTrend trend = WeeklyTrendResponse.CategoryTrend.builder()
                    .categoryId(yesterdayUsage.getCategoryId())
                    .categoryName(categoryCode)
                    .yesterdayMinutes(yesterdayMinutes)
                    .weeklyAverageMinutes(weeklyAverage)
                    .changeMinutes(changeMinutes)
                    .changeRate(changeRate)
                    .build();

            trends.add(trend);
        }

        // 변화율 절대값 기준으로 정렬하여 TOP 3 반환
        return trends.stream()
                .sorted(Comparator.comparingDouble((WeeklyTrendResponse.CategoryTrend t) ->
                        Math.abs(t.getChangeRate())).reversed())
                .limit(3)
                .collect(Collectors.toList());
    }

    /**
     * 카테고리별 7일 평균 계산
     */
    private Map<String, Double> calculateCategoryAverages(List<DailyCategoryUsage> weeklyUsages) {
        // 카테고리별로 그룹화하여 평균 계산
        Map<String, List<Integer>> categoryUsagesByCode = weeklyUsages.stream()
                .collect(Collectors.groupingBy(
                        DailyCategoryUsage::getCategoryCode,
                        Collectors.mapping(DailyCategoryUsage::getUsageMinutes, Collectors.toList())
                ));

        Map<String, Double> averages = new HashMap<>();
        for (Map.Entry<String, List<Integer>> entry : categoryUsagesByCode.entrySet()) {
            String categoryCode = entry.getKey();
            List<Integer> usages = entry.getValue();

            double average = usages.stream()
                    .mapToInt(Integer::intValue)
                    .average()
                    .orElse(0.0);

            averages.put(categoryCode, average);
        }

        return averages;
    }

    /**
     * 빈 총 사용시간 변화 객체 생성
     */
    private WeeklyTrendResponse.TotalUsageChange buildEmptyTotalUsageChange() {
        return WeeklyTrendResponse.TotalUsageChange.builder()
                .yesterdayMinutes(0)
                .weeklyAverageMinutes(0.0)
                .changeMinutes(0.0)
                .changeRate(0.0)
                .build();
    }
}
