package screentimeai.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import screentimeai.domain.entity.InsightDailyCategoryMetricsEntity;
import screentimeai.domain.entity.InsightDailySummaryEntity;
import screentimeai.domain.model.CategoryStatistics;
import screentimeai.infra.repository.InsightDailyCategoryMetricsRepository;
import screentimeai.infra.repository.InsightDailySummaryRepository;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 동적 임계값 계산 서비스
 *
 * Python의 improved_threshold_calculator.py 로직을 Java로 구현
 *
 * 실제 사용자 데이터를 기반으로 통계적 임계값을 계산합니다.
 * (현재는 고정 임계값을 사용하지만, 추후 데이터가 충분히 쌓이면 이 서비스를 활성화)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ThresholdCalculatorService {

    private final InsightDailySummaryRepository summaryRepository;
    private final InsightDailyCategoryMetricsRepository categoryMetricsRepository;

    /**
     * 전체 사용자 데이터를 기반으로 카테고리별 임계값 계산
     *
     * @param daysToAnalyze 분석할 일수 (예: 최근 30일)
     * @return 카테고리별 통계 및 임계값
     */
    public Map<String, CategoryStatistics> calculateThresholds(int daysToAnalyze) {
        log.info("임계값 계산 시작: 최근 {}일 데이터 분석", daysToAnalyze);

        LocalDate startDate = LocalDate.now().minusDays(daysToAnalyze);

        // 1. 전체 사용자의 일일 요약 데이터 조회
        List<InsightDailySummaryEntity> allSummaries = summaryRepository.findAll();
        log.info("전체 사용자 데이터: {}건", allSummaries.size());

        if (allSummaries.isEmpty()) {
            log.warn("데이터가 없어 기본 임계값을 사용합니다");
            return getDefaultThresholds();
        }

        // 2. 사용자별 프로필 구성
        Map<UUID, UserProfile> userProfiles = buildUserProfiles(allSummaries, startDate);
        log.info("분석 대상 사용자: {}명", userProfiles.size());

        // 3. 카테고리별 임계값 계산
        Map<String, CategoryStatistics> thresholds = new HashMap<>();
        List<String> categories = Arrays.asList("social_comm", "game", "learning", "entertainment");

        for (String category : categories) {
            CategoryStatistics stats = calculateCategoryThreshold(category, userProfiles);
            thresholds.put(category, stats);

            log.info("카테고리 [{}]: 비율 임계값 {:.1f}%, 시간 임계값 {:.2f}시간",
                    category,
                    stats.getRatioThreshold() * 100,
                    stats.getTimeThresholdSeconds() / 3600.0);
        }

        // 4. 강박적 확인형 임계값 계산
        int pickupThreshold = calculatePickupThreshold(userProfiles);
        log.info("강박적 확인형 픽업 임계값: {}회", pickupThreshold);

        return thresholds;
    }

    /**
     * 카테고리별 임계값 계산
     *
     * Python 로직:
     * - 지배적 사용자 평균 (40%)
     * - 75th percentile (30%)
     * - Mean + 0.5*Std (20%)
     * - 실제 사용자 평균 (10%)
     */
    private CategoryStatistics calculateCategoryThreshold(
            String category,
            Map<UUID, UserProfile> userProfiles
    ) {
        List<UserProfile> profiles = new ArrayList<>(userProfiles.values());

        // 해당 카테고리를 주로 사용하는 사용자들
        List<UserProfile> dominantUsers = profiles.stream()
                .filter(p -> category.equals(p.dominantCategory))
                .collect(Collectors.toList());

        // 1. 지배적 사용자 평균 비율
        double dominantAvg = dominantUsers.isEmpty() ? 0.3 :
                dominantUsers.stream()
                        .mapToDouble(p -> p.categoryRatios.getOrDefault(category, 0.0))
                        .average()
                        .orElse(0.3);

        // 2. 75th percentile
        List<Double> ratios = profiles.stream()
                .map(p -> p.categoryRatios.getOrDefault(category, 0.0))
                .sorted()
                .collect(Collectors.toList());
        double percentile75 = calculatePercentile(ratios, 75);

        // 3. Mean + 0.5*Std
        double mean = ratios.stream().mapToDouble(Double::doubleValue).average().orElse(0.2);
        double variance = ratios.stream()
                .mapToDouble(r -> Math.pow(r - mean, 2))
                .average()
                .orElse(0.0);
        double std = Math.sqrt(variance);
        double meanPlusHalfStd = mean + 0.5 * std;

        // 4. 실제 사용자 평균 (비율 > 0인 사용자만)
        double actualAvg = profiles.stream()
                .map(p -> p.categoryRatios.getOrDefault(category, 0.0))
                .filter(r -> r > 0)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.2);

        // 가중 평균 (40% + 30% + 20% + 10%)
        double ratioThreshold = dominantAvg * 0.4 + percentile75 * 0.3 + meanPlusHalfStd * 0.2 + actualAvg * 0.1;

        // 반올림 (0.05 단위)
        ratioThreshold = Math.round(ratioThreshold * 20) / 20.0;

        // 하한선: 최소 10%
        ratioThreshold = Math.max(ratioThreshold, 0.1);

        // === 시간 임계값 계산 ===

        // 지배적 사용자들의 평균 시간
        double dominantTime = dominantUsers.isEmpty() ? 3600 :
                dominantUsers.stream()
                        .mapToDouble(p -> p.categoryTimes.getOrDefault(category, 0.0))
                        .average()
                        .orElse(3600.0);

        // 실제 사용자들의 중앙값 시간
        List<Double> times = profiles.stream()
                .map(p -> p.categoryTimes.getOrDefault(category, 0.0))
                .filter(t -> t > 0)
                .sorted()
                .collect(Collectors.toList());
        double medianTime = times.isEmpty() ? 1800 : calculatePercentile(times, 50);

        // 평균 시간의 50%와 중앙값의 50% 혼합
        double timeThreshold = (dominantTime * 0.5 + medianTime * 0.5);

        // 반올림 (600초 = 10분 단위)
        timeThreshold = Math.round(timeThreshold / 600) * 600;

        // 하한선: 최소 5분
        timeThreshold = Math.max(timeThreshold, 300);

        return CategoryStatistics.builder()
                .category(category)
                .ratioThreshold(ratioThreshold)
                .timeThresholdSeconds((int) timeThreshold)
                .dominantAvg(dominantAvg)
                .percentile75(percentile75)
                .meanPlusHalfStd(meanPlusHalfStd)
                .actualAvg(actualAvg)
                .dominantTimeHours(dominantTime / 3600.0)
                .medianTimeHours(medianTime / 3600.0)
                .build();
    }

    /**
     * 강박적 확인형 픽업 임계값 계산 (80th percentile)
     */
    private int calculatePickupThreshold(Map<UUID, UserProfile> userProfiles) {
        List<Double> pickupCounts = userProfiles.values().stream()
                .map(p -> (double) p.avgDailyPickupCount)
                .sorted()
                .collect(Collectors.toList());

        double p80 = calculatePercentile(pickupCounts, 80);

        // 50회 단위로 반올림
        int threshold = (int) (Math.round(p80 / 50) * 50);

        return Math.max(threshold, 100);  // 최소 100회
    }

    /**
     * 사용자별 프로필 구성
     */
    private Map<UUID, UserProfile> buildUserProfiles(
            List<InsightDailySummaryEntity> allSummaries,
            LocalDate startDate
    ) {
        Map<UUID, List<InsightDailySummaryEntity>> groupedByUser = allSummaries.stream()
                .filter(s -> s.getSummaryDate().isAfter(startDate) || s.getSummaryDate().isEqual(startDate))
                .collect(Collectors.groupingBy(InsightDailySummaryEntity::getSubjectId));

        Map<UUID, UserProfile> profiles = new HashMap<>();

        for (Map.Entry<UUID, List<InsightDailySummaryEntity>> entry : groupedByUser.entrySet()) {
            UUID userId = entry.getKey();
            List<InsightDailySummaryEntity> userSummaries = entry.getValue();

            // 카테고리별 메트릭 조회
            List<InsightDailyCategoryMetricsEntity> categoryMetrics =
                    categoryMetricsRepository.findBySubjectIdAndDateRange(
                            userId,
                            startDate,
                            LocalDate.now()
                    );

            UserProfile profile = buildUserProfile(userId, userSummaries, categoryMetrics);
            profiles.put(userId, profile);
        }

        return profiles;
    }

    /**
     * 개별 사용자 프로필 구성
     */
    private UserProfile buildUserProfile(
            UUID userId,
            List<InsightDailySummaryEntity> summaries,
            List<InsightDailyCategoryMetricsEntity> categoryMetrics
    ) {
        // 평균 계산
        double avgTotalUsage = summaries.stream()
                .mapToInt(InsightDailySummaryEntity::getTotalUsageMinutes)
                .average()
                .orElse(0.0) * 60;  // 초 단위로 변환

        double avgPickupCount = summaries.stream()
                .mapToInt(InsightDailySummaryEntity::getDailyPickupCount)
                .average()
                .orElse(0.0);

        // 카테고리별 시간 계산
        Map<String, Double> categoryTimes = new HashMap<>();
        Map<Integer, Double> categoryIdToTime = categoryMetrics.stream()
                .collect(Collectors.groupingBy(
                        InsightDailyCategoryMetricsEntity::getCategoryId,
                        Collectors.averagingDouble(m -> m.getUsageMinutes() * 60.0)  // 초 단위
                ));

        // 카테고리 ID를 이름으로 매핑 (1=게임, 2=소셜, 3=학습, 4=엔터)
        categoryTimes.put("game", categoryIdToTime.getOrDefault(1, 0.0));
        categoryTimes.put("social_comm", categoryIdToTime.getOrDefault(2, 0.0));
        categoryTimes.put("learning", categoryIdToTime.getOrDefault(3, 0.0));
        categoryTimes.put("entertainment", categoryIdToTime.getOrDefault(4, 0.0));

        // 비율 계산
        Map<String, Double> categoryRatios = new HashMap<>();
        for (String cat : categoryTimes.keySet()) {
            double ratio = avgTotalUsage > 0 ? categoryTimes.get(cat) / avgTotalUsage : 0.0;
            categoryRatios.put(cat, ratio);
        }

        // 지배적 카테고리
        String dominantCategory = categoryRatios.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("game");

        return UserProfile.builder()
                .userId(userId)
                .avgTotalScreenTimeSeconds(avgTotalUsage)
                .avgDailyPickupCount((int) avgPickupCount)
                .categoryRatios(categoryRatios)
                .categoryTimes(categoryTimes)
                .dominantCategory(dominantCategory)
                .build();
    }

    /**
     * Percentile 계산
     */
    private double calculatePercentile(List<Double> sortedValues, int percentile) {
        if (sortedValues.isEmpty()) {
            return 0.0;
        }

        int index = (int) Math.ceil(percentile / 100.0 * sortedValues.size()) - 1;
        index = Math.max(0, Math.min(index, sortedValues.size() - 1));

        return sortedValues.get(index);
    }

    /**
     * 기본 임계값 반환 (데이터가 없을 때)
     */
    private Map<String, CategoryStatistics> getDefaultThresholds() {
        return Map.of(
                "social_comm", CategoryStatistics.builder()
                        .category("social_comm")
                        .ratioThreshold(0.25)
                        .timeThresholdSeconds(3600)
                        .build(),
                "game", CategoryStatistics.builder()
                        .category("game")
                        .ratioThreshold(0.30)
                        .timeThresholdSeconds(7200)
                        .build(),
                "learning", CategoryStatistics.builder()
                        .category("learning")
                        .ratioThreshold(0.20)
                        .timeThresholdSeconds(1800)
                        .build(),
                "entertainment", CategoryStatistics.builder()
                        .category("entertainment")
                        .ratioThreshold(0.25)
                        .timeThresholdSeconds(3600)
                        .build()
        );
    }

    /**
     * 사용자 프로필 (내부 클래스)
     */
    @lombok.Builder
    @lombok.Getter
    private static class UserProfile {
        private UUID userId;
        private double avgTotalScreenTimeSeconds;
        private int avgDailyPickupCount;
        private Map<String, Double> categoryRatios;
        private Map<String, Double> categoryTimes;
        private String dominantCategory;
    }
}
