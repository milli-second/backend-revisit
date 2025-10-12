package screentimeai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import screentimeai.domain.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DailyMetricsBatchService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final DailyUserMetricsRepository metricsRepository;
    private final DailyCategoryUsageRepository categoryUsageRepository;
    private final DailyTopAppsRepository topAppsRepository;
    private final CategoryMappingService categoryMappingService;

    private static final String REDIS_KEY_PREFIX = "daily_metrics:";

    /**
     * 매일 자정에 실행: Redis 데이터를 DB에 저장하고 Redis 삭제
     */
    @Scheduled(cron = "0 0 0 * * *")  // 매일 00:00:00
    public void saveDailyMetricsToDB() {
        log.info("Starting daily metrics batch job...");

        LocalDate yesterday = LocalDate.now().minusDays(1);
        String keyPattern = REDIS_KEY_PREFIX + "*:" + yesterday;

        // Redis에서 어제 날짜의 모든 키 조회
        Set<String> keys = redisTemplate.keys(keyPattern);

        if (keys == null || keys.isEmpty()) {
            log.info("No metrics found for yesterday: {}", yesterday);
            return;
        }

        int savedCount = 0;
        for (String redisKey : keys) {
            try {
                // Redis 키에서 subject_id 추출
                UUID subjectId = extractSubjectId(redisKey);

                // Redis 데이터 조회
                Map<Object, Object> redisData = redisTemplate.opsForHash().entries(redisKey);

                // 1. DailyUserMetrics 저장
                DailyUserMetrics metrics = convertToMetrics(subjectId, yesterday, redisData);
                metricsRepository.save(metrics);

                // 2. DailyCategoryUsage 저장
                List<DailyCategoryUsage> categoryUsages = extractCategoryUsages(subjectId, yesterday, redisData);
                if (!categoryUsages.isEmpty()) {
                    categoryUsageRepository.saveAll(categoryUsages);
                }

                // 3. DailyTopApps 저장
                List<DailyTopApps> topApps = extractTopApps(subjectId, yesterday, redisData);
                if (!topApps.isEmpty()) {
                    topAppsRepository.saveAll(topApps);
                }

                // Redis 데이터 삭제
                redisTemplate.delete(redisKey);

                savedCount++;
                log.debug("Saved metrics for subject: {}, date: {}", subjectId, yesterday);

            } catch (Exception e) {
                log.error("Failed to process metrics for key: {}", redisKey, e);
            }
        }

        log.info("Daily metrics batch job completed. Saved {} records for date: {}", savedCount, yesterday);
    }

    private UUID extractSubjectId(String redisKey) {
        // "daily_metrics:UUID:DATE" 형식에서 UUID 추출
        String[] parts = redisKey.split(":");
        return UUID.fromString(parts[1]);
    }

    private DailyUserMetrics convertToMetrics(UUID subjectId, LocalDate date, Map<Object, Object> redisData) {
        int totalMinutes = getIntValue(redisData, "total_usage_minutes");

        return DailyUserMetrics.builder()
                .subjectId(subjectId)
                .summaryDate(date)
                .totalUsageMinutes(totalMinutes)
                .dawnMinutes(getIntValue(redisData, "dawn_minutes"))
                .morningMinutes(getIntValue(redisData, "morning_minutes"))
                .afternoonMinutes(getIntValue(redisData, "afternoon_minutes"))
                .eveningMinutes(getIntValue(redisData, "evening_minutes"))
                .dawnSnsMinutes(getIntValue(redisData, "dawn_sns_minutes"))
                .mostActiveHourStart(calculateMostActiveHour(redisData))
                .dailyPickupCount(getIntValue(redisData, "daily_pickup_count"))
                .gameRatio(calculateCategoryRatio(redisData, CategoryMappingService.CATEGORY_GAME, totalMinutes))
                .socialCommRatio(calculateCategoryRatio(redisData, CategoryMappingService.CATEGORY_SOCIAL_COMM, totalMinutes))
                .learningRatio(calculateCategoryRatio(redisData, CategoryMappingService.CATEGORY_LEARNING, totalMinutes))
                .entertainmentRatio(calculateCategoryRatio(redisData, CategoryMappingService.CATEGORY_ENTERTAINMENT, totalMinutes))
                .build();
    }

    /**
     * Redis 데이터에서 카테고리별 사용량 추출
     */
    private List<DailyCategoryUsage> extractCategoryUsages(UUID subjectId, LocalDate date,
                                                            Map<Object, Object> redisData) {
        List<DailyCategoryUsage> usages = new ArrayList<>();

        for (int categoryId = 1; categoryId <= 5; categoryId++) {
            int usageMinutes = getIntValue(redisData, "category_" + categoryId);
            if (usageMinutes > 0) {
                DailyCategoryUsage usage = DailyCategoryUsage.builder()
                        .subjectId(subjectId)
                        .summaryDate(date)
                        .categoryId(categoryId)
                        .usageMinutes(usageMinutes)
                        .build();
                usages.add(usage);
            }
        }

        return usages;
    }

    /**
     * Redis 데이터에서 Top 3 앱 추출
     */
    private List<DailyTopApps> extractTopApps(UUID subjectId, LocalDate date,
                                                Map<Object, Object> redisData) {
        // "app:" prefix로 시작하는 키들을 필터링하여 앱별 사용시간 추출
        Map<String, Integer> appUsageMap = new HashMap<>();

        for (Map.Entry<Object, Object> entry : redisData.entrySet()) {
            String key = entry.getKey().toString();
            if (key.startsWith("app:")) {
                String packageName = key.substring(4); // "app:" 제거
                int usageMinutes = Integer.parseInt(entry.getValue().toString());
                appUsageMap.put(packageName, usageMinutes);
            }
        }

        // 사용시간 기준으로 정렬하여 Top 3 추출
        List<Map.Entry<String, Integer>> sortedApps = appUsageMap.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(3)
                .collect(Collectors.toList());

        List<DailyTopApps> topApps = new ArrayList<>();
        for (int i = 0; i < sortedApps.size(); i++) {
            Map.Entry<String, Integer> entry = sortedApps.get(i);
            DailyTopApps topApp = DailyTopApps.builder()
                    .subjectId(subjectId)
                    .summaryDate(date)
                    .appRank(i + 1)
                    .packageName(entry.getKey())
                    .usageMinutes(entry.getValue())
                    .build();
            topApps.add(topApp);
        }

        return topApps;
    }

    private int getIntValue(Map<Object, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) return 0;
        return Integer.parseInt(value.toString());
    }

    private Integer calculateMostActiveHour(Map<Object, Object> data) {
        int maxUsage = 0;
        int mostActiveHour = 0;

        for (int hour = 0; hour < 24; hour++) {
            int usage = getIntValue(data, "hour_" + hour);
            if (usage > maxUsage) {
                maxUsage = usage;
                mostActiveHour = hour;
            }
        }

        return maxUsage > 0 ? mostActiveHour : null;
    }

    private Float calculateCategoryRatio(Map<Object, Object> data, int categoryId, int totalMinutes) {
        if (totalMinutes == 0) return 0f;
        int categoryMinutes = getIntValue(data, "category_" + categoryId);
        return (float) categoryMinutes / totalMinutes;
    }
}
