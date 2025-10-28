package screentimeai.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import screentimeai.domain.entity.*;
import screentimeai.domain.service.SnsInsightService;
import screentimeai.infra.repository.DailyCategoryUsageRepository;
import screentimeai.infra.repository.DailyUserMetricsRepository;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DailyMetricsBatchService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final DailyUserMetricsRepository metricsRepository;
    private final DailyCategoryUsageRepository categoryUsageRepository;

    private final AppUsageTracker appUsageTracker;
    private final AbstractEventPublisher eventPublisher;
    private final SnsInsightService snsInsightService;
    private final CategoryMappingService categoryMappingService;

    private static final String REDIS_KEY_PREFIX = "daily_metrics:";

    /**
     * 자정 5분 전 실행: 모든 활성 세션을 자정까지 집계하고 종료
     */
    @Scheduled(cron = "0 55 23 * * *")  // 매일 23:55:00
    public void closeActiveSessionsBeforeMidnight() {
        log.info("Starting to close all active sessions before midnight...");

        try {
            // 모든 활성 foreground 세션 찾기
            String pattern = "foreground_app:*";
            Set<String> keys = redisTemplate.keys(pattern);

            if (keys.isEmpty()) {
                log.info("No active sessions found");
                return;
            }

            // subjectId별로 그룹화
            Map<UUID, Integer> closedSessionsByUser = new HashMap<>();
            java.time.Instant midnight = java.time.LocalDate.now()
                    .plusDays(1)
                    .atStartOfDay(java.time.ZoneId.systemDefault())
                    .toInstant();

            for (String key : keys) {
                try {
                    // foreground_app:UUID:packageName 형식에서 UUID 추출
                    String[] parts = key.split(":");
                    if (parts.length < 2) continue;

                    UUID subjectId = UUID.fromString(parts[1]);

                    // 해당 사용자의 모든 활성 세션 종료 (utcOffset은 9로 가정, 실제로는 Redis에서 가져와야 함)
                    Map<Object, Object> foregroundInfo = redisTemplate.opsForHash().entries(key);
                    int utcOffset = foregroundInfo.get("utcOffset") != null
                            ? Integer.parseInt(foregroundInfo.get("utcOffset").toString())
                            : 9;

                    appUsageTracker.closeAllActiveSessions(subjectId, midnight, utcOffset);
                    closedSessionsByUser.merge(subjectId, 1, Integer::sum);

                } catch (Exception e) {
                    log.error("Failed to close session for key: {}", key, e);
                }
            }

            log.info("Closed active sessions for {} users before midnight", closedSessionsByUser.size());

        } catch (Exception e) {
            log.error("Failed to close active sessions before midnight", e);
        }
    }


    /**
     * 매일 자정에 실행: Redis 데이터를 DB에 저장하고 Redis 삭제
     */

    @Scheduled(cron = "0 0 0 * * *")  // 매일 00:00:00
    //@Scheduled(fixedRate = 30000) // 테스트용
    public void saveDailyMetricsToDB() {
        log.info("Starting daily metrics batch job...");

        //TODO: 배포시 어제로 다시 바꿔놓기. 지금은 오늘로 되어 있음
        LocalDate yesterday = LocalDate.now().minusDays(1);
        String keyPattern = REDIS_KEY_PREFIX + "*:" + yesterday;

        // Redis에서 어제 날짜의 모든 키 조회
        Set<String> keys = redisTemplate.keys(keyPattern);

        if ( keys.isEmpty()) {
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

                // 3. SNS 인사이트 계산 및 저장
                try {
                    snsInsightService.calculateAndSave(subjectId, yesterday);
                    log.debug("SNS insight calculated for subject: {}, date: {}", subjectId, yesterday);
                } catch (Exception e) {
                    log.error("Failed to calculate SNS insight for subject: {}, date: {}", subjectId, yesterday, e);
                }

                // 4. 이벤트 발행
                DailyMetricsSaved event = new DailyMetricsSaved(metrics);
                eventPublisher.publish("produceDailyMetricsSaved", event, "DailyMetricsSaved");
                log.info("Published DailyMetricsSaved event for subject: {}, date: {}", subjectId, yesterday);

                //TODO: 배포시 되돌려 놓기
//                // Redis 데이터 삭제
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
        // 밀리초 값을 우선 사용, 없으면 기존 분 값 사용 (호환성)
        long totalMillis = getLongValue(redisData, "total_usage_millis");
        int totalMinutes = totalMillis > 0
                ? (int) Math.round(totalMillis / 60000.0)
                : getIntValue(redisData, "total_usage_minutes");

        return DailyUserMetrics.builder()
                .subjectId(subjectId)
                .summaryDate(date)
                .totalUsageMinutes(totalMinutes)
                .dawnMinutes(convertMillisToMinutes(redisData, "dawn_millis", "dawn_minutes"))
                .morningMinutes(convertMillisToMinutes(redisData, "morning_millis", "morning_minutes"))
                .afternoonMinutes(convertMillisToMinutes(redisData, "afternoon_millis", "afternoon_minutes"))
                .eveningMinutes(convertMillisToMinutes(redisData, "evening_millis", "evening_minutes"))
                .dawnSnsMinutes(convertMillisToMinutes(redisData, "dawn_sns_millis", "dawn_sns_minutes"))
                .mostActiveHourStart(calculateMostActiveHour(redisData))
                .dailyPickupCount(getIntValue(redisData, "daily_pickup_count"))
                .gameRatio(calculateCategoryRatio(redisData, CategoryMappingService.CATEGORY_GAME, totalMinutes))
                .socialCommRatio(calculateCategoryRatio(redisData, CategoryMappingService.CATEGORY_SOCIAL_COMM, totalMinutes))
                .learningRatio(calculateCategoryRatio(redisData, CategoryMappingService.CATEGORY_LEARNING, totalMinutes))
                .entertainmentRatio(calculateCategoryRatio(redisData, CategoryMappingService.CATEGORY_ENTERTAINMENT, totalMinutes))
                .build();
    }

    /**
     * 밀리초 값을 분으로 변환 (밀리초 값 우선, 없으면 기존 분 값 사용)
     */
    private int convertMillisToMinutes(Map<Object, Object> data, String millisKey, String minutesKey) {
        long millis = getLongValue(data, millisKey);
        if (millis > 0) {
            return (int) Math.round(millis / 60000.0);
        }
        return getIntValue(data, minutesKey);
    }

    private long getLongValue(Map<Object, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) return 0L;
        return Long.parseLong(value.toString());
    }

    /**
     * Redis 데이터에서 카테고리별 사용량 추출 (원본 Google Play 카테고리 코드 포함)
     */
    private List<DailyCategoryUsage> extractCategoryUsages(UUID subjectId, LocalDate date,
                                                            Map<Object, Object> redisData) {
        // 패키지별 사용량을 원본 카테고리 코드로 그룹화
        Map<String, Integer> categoryCodeUsage = new HashMap<>();
        Map<String, Integer> categoryIdMap = new HashMap<>();

        for (Map.Entry<Object, Object> entry : redisData.entrySet()) {
            String key = entry.getKey().toString();

            // "app_millis:{packageName}:{appName}" 형식의 키 우선 찾기
            if (key.startsWith("app_millis:")) {
                String[] parts = key.split(":", 3);
                if (parts.length >= 2) {
                    String packageName = parts[1];
                    long usageMillis = getLongValue(redisData, key);
                    int usageMinutes = (int) Math.round(usageMillis / 60000.0);

                    if (usageMinutes > 0) {
                        // 원본 카테고리 코드 조회
                        String categoryCode = categoryMappingService.getCategoryCode(packageName);
                        int categoryId = categoryMappingService.getCategoryId(packageName);

                        // 카테고리별로 합산
                        categoryCodeUsage.merge(categoryCode, usageMinutes, Integer::sum);
                        categoryIdMap.put(categoryCode, categoryId);
                    }
                }
            } else if (key.startsWith("app:")) {
                // 기존 분 단위 데이터 호환성
                String[] parts = key.split(":", 3);
                if (parts.length >= 2) {
                    String packageName = parts[1];
                    // 밀리초 키가 없는 경우에만 처리
                    String millisKey = "app_millis:" + parts[1] + ":" + (parts.length > 2 ? parts[2] : "");
                    if (!redisData.containsKey(millisKey)) {
                        int usageMinutes = getIntValue(redisData, key);

                        if (usageMinutes > 0) {
                            String categoryCode = categoryMappingService.getCategoryCode(packageName);
                            int categoryId = categoryMappingService.getCategoryId(packageName);

                            categoryCodeUsage.merge(categoryCode, usageMinutes, Integer::sum);
                            categoryIdMap.put(categoryCode, categoryId);
                        }
                    }
                }
            }
        }

        // DailyCategoryUsage 리스트 생성
        List<DailyCategoryUsage> usages = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : categoryCodeUsage.entrySet()) {
            String categoryCode = entry.getKey();
            int usageMinutes = entry.getValue();
            int categoryId = categoryIdMap.get(categoryCode);

            DailyCategoryUsage usage = DailyCategoryUsage.builder()
                    .subjectId(subjectId)
                    .summaryDate(date)
                    .categoryId(categoryId)
                    .categoryCode(categoryCode)
                    .usageMinutes(usageMinutes)
                    .build();
            usages.add(usage);
        }

        return usages;
    }

    /**
     * Redis 데이터에서 Top 3 앱 추출
     */


    private int getIntValue(Map<Object, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) return 0;
        return Integer.parseInt(value.toString());
    }

    /**
     * 가장 활발한 2시간 연속 사용 구간의 시작 시간 계산
     * @return 2시간 윈도우 시작 시간 (0-22), 사용량이 없으면 null
     */
    private Integer calculateMostActiveHour(Map<Object, Object> data) {
        // Extract hourly usage data (0-23) - 밀리초 값 우선 사용
        long[] hourlyMillis = new long[24];
        for (int hour = 0; hour < 24; hour++) {
            long millis = getLongValue(data, "hour_millis_" + hour);
            if (millis > 0) {
                hourlyMillis[hour] = millis;
            } else {
                // 기존 분 값을 밀리초로 변환 (호환성)
                hourlyMillis[hour] = getIntValue(data, "hour_" + hour) * 60000L;
            }
        }

        // Find max 2-hour window
        long maxTwoHourUsage = 0;
        int mostActiveHourStart = -1;

        for (int i = 0; i < 23; i++) {
            long twoHourUsage = hourlyMillis[i] + hourlyMillis[i+1];
            if (twoHourUsage > maxTwoHourUsage) {
                maxTwoHourUsage = twoHourUsage;
                mostActiveHourStart = i;
            }
        }

        return mostActiveHourStart != -1 ? mostActiveHourStart : null;
    }

    private Float calculateCategoryRatio(Map<Object, Object> data, int categoryId, int totalMinutes) {
        if (totalMinutes == 0) return 0f;

        // 밀리초 값 우선 사용
        long categoryMillis = getLongValue(data, "category_millis_" + categoryId);
        int categoryMinutes = categoryMillis > 0
                ? (int) Math.round(categoryMillis / 60000.0)
                : getIntValue(data, "category_" + categoryId);

        return (float) categoryMinutes / totalMinutes;
    }

    /**
     * 과거 데이터(24시간 이전)를 즉시 배치 처리하여 DB에 저장
     * 2-3일 전 데이터가 한꺼번에 들어올 때 사용
     */
    public void processHistoricalDataBatch(List<RawData> historicalDataList) {
        if (historicalDataList.isEmpty()) {
            log.info("Historical data list is empty");
            return;
        }

        log.info("Processing {} historical events for immediate aggregation", historicalDataList.size());

        // 1. 날짜별 + 사용자별로 그룹화
        Map<String, List<RawData>> groupedByUserAndDate = historicalDataList.stream()
                .collect(Collectors.groupingBy(data -> {
                    UUID subjectId = data.getSubjectId();
                    int utcOffset = data.getUtcoffset() != null ? data.getUtcoffset() : 9;
                    LocalDate date = getLocalDateFromInstant(data.getTimestamp(), utcOffset);
                    return subjectId + ":" + date;
                }));

        log.info("Grouped into {} user-date combinations", groupedByUserAndDate.size());

        // 2. 각 그룹별로 처리
        int processedGroups = 0;
        for (Map.Entry<String, List<RawData>> entry : groupedByUserAndDate.entrySet()) {
            String[] parts = entry.getKey().split(":");
            UUID subjectId = UUID.fromString(parts[0]);
            LocalDate date = LocalDate.parse(parts[1]);
            List<RawData> dailyData = entry.getValue();

            try {
                processHistoricalDataForDate(subjectId, date, dailyData);
                processedGroups++;
                log.debug("Processed {} events for subjectId={}, date={}",
                        dailyData.size(), subjectId, date);
            } catch (Exception e) {
                log.error("Failed to process historical data for subjectId={}, date={}",
                        subjectId, date, e);
            }
        }

        log.info("Historical batch processing completed: {} groups processed", processedGroups);
    }

    /**
     * 특정 날짜의 과거 데이터를 집계하여 DB에 저장
     */
    private void processHistoricalDataForDate(UUID subjectId, LocalDate date, List<RawData> dailyData) {

        try {
            for (RawData rawData : dailyData) {
                // AppUsageTracker는 자동으로 올바른 날짜 키에 집계하므로,
                // 여기서는 직접 집계 로직을 호출
                appUsageTracker.processEvent(rawData);
            }

            // 2. 집계된 Redis 데이터를 조회
            String dailyKey = REDIS_KEY_PREFIX + subjectId + ":" + date;
            Map<Object, Object> redisData = redisTemplate.opsForHash().entries(dailyKey);

            // 3. DB에 저장 (기존 데이터가 있으면 업데이트)
            DailyUserMetrics metrics = saveDailyMetricsToDb(subjectId, date, redisData);

            // --- [ ✨ 3. SNS 인사이트 계산 로직 추가 ] ---
            // saveDailyMetricsToDB와 동일한 로직
            try {
                // 'yesterday' 대신 'date' 변수 사용
                snsInsightService.calculateAndSave(subjectId, date);
                log.debug("SNS insight calculated for subject: {}, date: {}", subjectId, date);
            } catch (Exception e) {
                log.error("Failed to calculate SNS insight for subject: {}, date: {}", subjectId, date, e);
            }

            // --- [ ✨ 4. 이벤트 발행 로직 추가 ] ---
            // saveDailyMetricsToDB와 동일한 로직
            DailyMetricsSaved event = new DailyMetricsSaved(metrics); // 반환받은 metrics 객체 사용
            eventPublisher.publish("produceDailyMetricsSaved", event, "DailyMetricsSaved");
            log.info("Published DailyMetricsSaved event for subject: {}, date: {}", subjectId, date);

            // 4. Redis 데이터 삭제 (과거 데이터는 즉시 DB로 이동)
            redisTemplate.delete(dailyKey);

            log.info("Historical data saved to DB: subjectId={}, date={}, events={}",
                    subjectId, date, dailyData.size());

        } catch (Exception e) {
            log.error("Failed to process historical data for subjectId={}, date={}",
                    subjectId, date, e);
            throw e;
        }
    }

    /**
     * Redis 집계 데이터를 DB에 저장 (기존 데이터와 병합)
     */
    private DailyUserMetrics saveDailyMetricsToDb(UUID subjectId, LocalDate date, Map<Object, Object> redisData) {
        // 1. 기존 DailyUserMetrics 조회 및 병합
        Optional<DailyUserMetrics> existingMetrics = metricsRepository
                .findBySubjectIdAndSummaryDate(subjectId, date);

        DailyUserMetrics metrics;
        if (existingMetrics.isPresent()) {
            // 기존 데이터가 있으면 병합
            log.info("Merging with existing metrics for subjectId={}, date={}", subjectId, date);
            metrics = mergeMetrics(existingMetrics.get(), redisData);
        } else {
            // 기존 데이터가 없으면 신규 생성
            metrics = convertToMetrics(subjectId, date, redisData);
        }
        metricsRepository.save(metrics);

        // 2. DailyCategoryUsage 병합 저장
        List<DailyCategoryUsage> categoryUsages = extractCategoryUsages(subjectId, date, redisData);
        if (!categoryUsages.isEmpty()) {
            mergeCategoryUsages(subjectId, date, categoryUsages);
        }

        return metrics;
    }

    /**
     * 기존 메트릭과 새로운 Redis 데이터를 병합
     */
    private DailyUserMetrics mergeMetrics(DailyUserMetrics existing, Map<Object, Object> redisData) {
        // 밀리초 값 우선 사용
        long newTotalMillis = getLongValue(redisData, "total_usage_millis");
        int newTotalMinutes = newTotalMillis > 0
                ? (int) Math.round(newTotalMillis / 60000.0)
                : getIntValue(redisData, "total_usage_minutes");
        int mergedTotal = existing.getTotalUsageMinutes() + newTotalMinutes;

        // 각 필드 합산 (밀리초 값 우선 사용)
        existing.setTotalUsageMinutes(mergedTotal);
        existing.setDawnMinutes(existing.getDawnMinutes() + convertMillisToMinutes(redisData, "dawn_millis", "dawn_minutes"));
        existing.setMorningMinutes(existing.getMorningMinutes() + convertMillisToMinutes(redisData, "morning_millis", "morning_minutes"));
        existing.setAfternoonMinutes(existing.getAfternoonMinutes() + convertMillisToMinutes(redisData, "afternoon_millis", "afternoon_minutes"));
        existing.setEveningMinutes(existing.getEveningMinutes() + convertMillisToMinutes(redisData, "evening_millis", "evening_minutes"));
        existing.setDawnSnsMinutes(existing.getDawnSnsMinutes() + convertMillisToMinutes(redisData, "dawn_sns_millis", "dawn_sns_minutes"));
        existing.setDailyPickupCount(existing.getDailyPickupCount() + getIntValue(redisData, "daily_pickup_count"));

        // most_active_hour는 기존 데이터와 비교하여 재계산
        Integer newMostActiveHour = calculateMostActiveHour(redisData);
        if (newMostActiveHour != null) {
            existing.setMostActiveHourStart(newMostActiveHour);
        }

        // 카테고리별 사용량을 DB에서 다시 조회하여 비율 재계산
        List<DailyCategoryUsage> existingCategories = categoryUsageRepository
                .findBySubjectIdAndSummaryDate(existing.getSubjectId(), existing.getSummaryDate());

        Map<Integer, Integer> categoryMinutes = new HashMap<>();
        for (DailyCategoryUsage cat : existingCategories) {
            categoryMinutes.put(cat.getCategoryId(), cat.getUsageMinutes());
        }

        // Redis에서 새로 들어온 카테고리 사용량 추가 (밀리초 우선)
        for (int categoryId = 1; categoryId <= 5; categoryId++) {
            long redisMillis = getLongValue(redisData, "category_millis_" + categoryId);
            int redisMinutes = redisMillis > 0
                    ? (int) Math.round(redisMillis / 60000.0)
                    : getIntValue(redisData, "category_" + categoryId);
            categoryMinutes.merge(categoryId, redisMinutes, Integer::sum);
        }

        // 카테고리 비율 재계산
        if (mergedTotal > 0) {
            existing.setGameRatio(calculateCategoryRatioForMerge(
                    CategoryMappingService.CATEGORY_GAME, categoryMinutes, mergedTotal));
            existing.setSocialCommRatio(calculateCategoryRatioForMerge(
                    CategoryMappingService.CATEGORY_SOCIAL_COMM, categoryMinutes, mergedTotal));
            existing.setLearningRatio(calculateCategoryRatioForMerge(
                    CategoryMappingService.CATEGORY_LEARNING, categoryMinutes, mergedTotal));
            existing.setEntertainmentRatio(calculateCategoryRatioForMerge(
                    CategoryMappingService.CATEGORY_ENTERTAINMENT, categoryMinutes, mergedTotal));
        }

        return existing;
    }

    /**
     * 카테고리 사용량 병합
     */
    private void mergeCategoryUsages(UUID subjectId, LocalDate date, List<DailyCategoryUsage> newUsages) {
        for (DailyCategoryUsage newUsage : newUsages) {
            DailyCategoryUsageId id = new DailyCategoryUsageId(
                    subjectId, date, newUsage.getCategoryId());

            Optional<DailyCategoryUsage> existing = categoryUsageRepository.findById(id);

            if (existing.isPresent()) {
                DailyCategoryUsage merged = existing.get();
                merged.setUsageMinutes(merged.getUsageMinutes() + newUsage.getUsageMinutes());
                // categoryCode도 업데이트 (혹시 변경되었을 경우를 대비)
                merged.setCategoryCode(newUsage.getCategoryCode());
                categoryUsageRepository.save(merged);
                log.debug("Merged category usage: categoryId={}, categoryCode={}, oldMinutes={}, newMinutes={}, total={}",
                        newUsage.getCategoryId(), newUsage.getCategoryCode(), existing.get().getUsageMinutes(),
                        newUsage.getUsageMinutes(), merged.getUsageMinutes());
            } else {
                categoryUsageRepository.save(newUsage);
            }
        }
    }



    /**
     * 병합 시 카테고리 비율 계산
     */
    private Float calculateCategoryRatioForMerge(int categoryId, Map<Integer, Integer> categoryMinutes, int totalMinutes) {
        if (totalMinutes == 0) return 0f;
        int minutes = categoryMinutes.getOrDefault(categoryId, 0);
        return (float) minutes / totalMinutes;
    }

    /**
     * Instant를 LocalDate로 변환 (UTC offset 고려)
     */
    private LocalDate getLocalDateFromInstant(java.time.Instant timestamp, int utcOffsetHours) {
        ZoneId zoneId = ZoneId.ofOffset("UTC", java.time.ZoneOffset.ofHours(utcOffsetHours));
        return timestamp.atZone(zoneId).toLocalDate();
    }
}