package screentimeai.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import screentimeai.domain.entity.RawData;
import screentimeai.presentation.dto.CategoryUsageResponse;
import screentimeai.presentation.dto.DailyUsageSummary;
import screentimeai.presentation.dto.TodayUsageSummary;
import screentimeai.presentation.dto.TopApp;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * MOVE_TO_FOREGROUND/BACKGROUND 이벤트를 기반으로
 * 실제 앱 사용시간을 계산하고 Redis에 집계하는 서비스
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AppUsageTracker {

    private final RedisTemplate<String, Object> redisTemplate;
    private final CategoryMappingService categoryMappingService;

    private static final String FOREGROUND_APP_PREFIX = "foreground_app:";
    private static final String DAILY_METRICS_PREFIX = "daily_metrics:";
    private static final String REALTIME_METRICS_PREFIX = "realtime_metrics:";
    private static final String LAST_SESSION_PREFIX = "last_session:";

    // Session TTL: 30분 (비정상 종료 감지용)
    private static final long SESSION_TTL_MINUTES = 30;
    // Heartbeat 간격: 5분 (장시간 사용 시 세션 연장용)
    private static final long HEARTBEAT_INTERVAL_MINUTES = 5;
    // 최소 사용 시간: 1초 (1초 미만은 무시)
    private static final long MIN_USAGE_MILLIS = 1000;
    // 세션 병합 임계값: 5초 (5초 이내 재시작은 동일 세션으로 간주)
    private static final long SESSION_MERGE_THRESHOLD_MILLIS = 5000;

    /**
     * RawData 이벤트를 처리하여 앱 사용시간 집계
     */
    public void processEvent(RawData rawData) {
        String eventType = rawData.getType();

        if ("MOVE_TO_FOREGROUND".equals(eventType)) {
            handleForegroundEvent(rawData);
        } else if ("MOVE_TO_BACKGROUND".equals(eventType)) {
            handleBackgroundEvent(rawData);
        } else if ("USER_INTERACTION".equals(eventType)) {
            handleUserInteractionEvent(rawData);
        }
    }

    /**
     * MOVE_TO_FOREGROUND: 앱이 포그라운드로 이동 (시작)
     */
    private void handleForegroundEvent(RawData rawData) {
        String key = buildForegroundKey(rawData.getSubjectId(), rawData.getPackageName());

        // 세션 병합: 5초 이내에 같은 앱이 다시 FOREGROUND로 오면 이전 세션 유지
        Long lastSessionEnd = getLastSessionEnd(rawData.getSubjectId(), rawData.getPackageName());
        long currentTimestamp = rawData.getTimestamp().toEpochMilli();

        if (lastSessionEnd != null && (currentTimestamp - lastSessionEnd) <= SESSION_MERGE_THRESHOLD_MILLIS) {
            // 기존 세션이 5초 이내에 종료되었으면 새 세션으로 시작하지 않고 무시
            // (실제로는 연속 사용 중)
            log.debug("Session merge detected: subjectId={}, package={}, gap={}ms",
                    rawData.getSubjectId(), rawData.getPackageName(), currentTimestamp - lastSessionEnd);
            // 기존 foreground 세션이 있으면 그대로 유지, 없으면 새로 시작
            Boolean exists = redisTemplate.hasKey(key);
            if (exists) {
                return; // 이미 활성 세션이 있으면 그대로 유지
            }
        }

        Map<String, Object> foregroundInfo = new HashMap<>();
        foregroundInfo.put("startTimestamp", rawData.getTimestamp().toEpochMilli());
        foregroundInfo.put("utcOffset", rawData.getUtcoffset());
        foregroundInfo.put("lastHeartbeat", rawData.getTimestamp().toEpochMilli());

        redisTemplate.opsForHash().putAll(key, foregroundInfo);
        // 30분 TTL (비정상 종료 감지용)
        redisTemplate.expire(key, SESSION_TTL_MINUTES, TimeUnit.MINUTES);

        log.debug("Foreground started: subjectId={}, package={}, timestamp={}",
                rawData.getSubjectId(), rawData.getPackageName(), rawData.getTimestamp());
    }

    /**
     * MOVE_TO_BACKGROUND: 앱이 백그라운드로 이동 (종료) → 사용시간 계산
     */
    private void handleBackgroundEvent(RawData rawData) {
        String key = buildForegroundKey(rawData.getSubjectId(), rawData.getPackageName());

        Map<Object, Object> foregroundInfo = redisTemplate.opsForHash().entries(key);

        if (foregroundInfo.isEmpty()) {
            log.debug("No foreground session found for subjectId={}, package={}",
                    rawData.getSubjectId(), rawData.getPackageName());
            return;
        }

        long startTimestamp = Long.parseLong(foregroundInfo.get("startTimestamp").toString());
        int utcOffset = foregroundInfo.get("utcOffset") != null
                ? Integer.parseInt(foregroundInfo.get("utcOffset").toString())
                : 0;

        long endTimestamp = rawData.getTimestamp().toEpochMilli();
        long usageMillis = endTimestamp - startTimestamp;

        // 비정상적인 사용시간 필터링 (0초 미만 or 24시간 초과)
        if (usageMillis < 0 || usageMillis > 24 * 60 * 60 * 1000L) {
            log.warn("Abnormal usage time detected: {}ms for package={}",
                    usageMillis, rawData.getPackageName());
            redisTemplate.delete(key);
            return;
        }

        // 1초 미만 세션 필터링
        if (usageMillis < MIN_USAGE_MILLIS) {
            log.debug("Too short session ignored: {}ms for package={}",
                    usageMillis, rawData.getPackageName());
            redisTemplate.delete(key);
            return;
        }

        int usageMinutes = (int) (usageMillis / 60000);

        // 사용시간 집계 (자정 넘김 고려)
        aggregateUsageTimeWithDateBoundary(rawData, Instant.ofEpochMilli(startTimestamp),
                rawData.getTimestamp(), usageMillis, utcOffset);

        // 마지막 세션 정보 저장 (세션 병합용)
        saveLastSession(rawData.getSubjectId(), rawData.getPackageName(), endTimestamp);

        // Foreground 세션 삭제
        redisTemplate.delete(key);

        log.debug("Usage recorded: subjectId={}, package={}, minutes={}",
                rawData.getSubjectId(), rawData.getPackageName(), usageMinutes);
    }

    /**
     * TODO: 픽업 카운트는 SCREEN_INTERACTIVE, SCREEN_NON_INTERACTIVE 로 계산해야함
     * USER_INTERACTION: 사용자 상호작용 (픽업 카운트 + Heartbeat)
     */
    private void handleUserInteractionEvent(RawData rawData) {
        int utcOffset = rawData.getUtcoffset() != null ? rawData.getUtcoffset() : 0;
        LocalDate date = getLocalDate(rawData.getTimestamp(), utcOffset);
        String dailyKey = buildDailyKey(rawData.getSubjectId(), date);

        // 픽업 카운트 증가
        redisTemplate.opsForHash().increment(dailyKey, "daily_pickup_count", 1);
        redisTemplate.expire(dailyKey, 48, TimeUnit.HOURS);

        // Heartbeat: 현재 foreground 앱의 세션 연장 (있다면)
        if (rawData.getPackageName() != null) {
            String foregroundKey = buildForegroundKey(rawData.getSubjectId(), rawData.getPackageName());
            Boolean exists = redisTemplate.hasKey(foregroundKey);
            if (exists) {
                redisTemplate.opsForHash().put(foregroundKey, "lastHeartbeat",
                        rawData.getTimestamp().toEpochMilli());
                redisTemplate.expire(foregroundKey, SESSION_TTL_MINUTES, TimeUnit.MINUTES);
                log.debug("Heartbeat: subjectId={}, package={}",
                        rawData.getSubjectId(), rawData.getPackageName());
            }
        }
    }

    /**
     * 사용시간을 Redis에 집계
     */
    private void aggregateUsageTime(RawData rawData, Instant startTime,
                                     int usageMinutes, int utcOffset) {
        UUID subjectId = rawData.getSubjectId();
        String packageName = rawData.getPackageName();
        LocalDate date = getLocalDate(startTime, utcOffset);

        String dailyKey = buildDailyKey(subjectId, date);
        String realtimeKey = buildRealtimeKey(subjectId);

        // 카테고리 정보
        int categoryId = categoryMappingService.getCategoryId(packageName);
        boolean isSocial = categoryMappingService.isSocialCategory(categoryId);

        // 1. 총 사용시간
        redisTemplate.opsForHash().increment(dailyKey, "total_usage_minutes", usageMinutes);

        // 2. 시간대별 사용시간 (시작 시간 기준)
        aggregateByTimeOfDay(dailyKey, startTime, usageMinutes, utcOffset, isSocial);

        // 3. 시간별 사용량 (most_active_hour 계산용)
        int hour = getHour(startTime, utcOffset);
        redisTemplate.opsForHash().increment(dailyKey, "hour_" + hour, usageMinutes);

        // 4. 카테고리별 사용시간
        redisTemplate.opsForHash().increment(dailyKey, "category_" + categoryId, usageMinutes);

        // 5. 앱별 사용시간 (Top 3 계산용)
        redisTemplate.opsForHash().increment(dailyKey, "app:" + packageName + ":" + rawData.getName(), usageMinutes);

        // 6. 실시간 메트릭 (24시간 슬라이딩 윈도우)
        aggregateRealtimeMetrics(realtimeKey, packageName, rawData.getName(), startTime, usageMinutes, categoryId);

        // TTL 설정 (48시간 후 자동 삭제)
        redisTemplate.expire(dailyKey, 48, TimeUnit.HOURS);
    }

    /**
     * 시간대별 사용시간 집계
     * Dawn: 0-6시, Morning: 6-12시, Afternoon: 12-18시, Evening: 18-24시
     */
    private void aggregateByTimeOfDay(String redisKey, Instant timestamp,
                                       int usageMinutes, int utcOffset, boolean isSocial) {
        int hour = getHour(timestamp, utcOffset);

        if (hour >= 0 && hour < 6) {
            redisTemplate.opsForHash().increment(redisKey, "dawn_minutes", usageMinutes);
            if (isSocial) {
                redisTemplate.opsForHash().increment(redisKey, "dawn_sns_minutes", usageMinutes);
            }
        } else if (hour >= 6 && hour < 12) {
            redisTemplate.opsForHash().increment(redisKey, "morning_minutes", usageMinutes);
        } else if (hour >= 12 && hour < 18) {
            redisTemplate.opsForHash().increment(redisKey, "afternoon_minutes", usageMinutes);
        } else {
            redisTemplate.opsForHash().increment(redisKey, "evening_minutes", usageMinutes);
        }
    }

    /**
     * 실시간 메트릭 집계 (현재 시간 기준 24시간)
     */
    private void aggregateRealtimeMetrics(String realtimeKey, String packageName, String appName,
                                           Instant timestamp, int usageMinutes, int categoryId) {
        // 24시간 이전 데이터는 실시간 메트릭에 추가하지 않음 (불필요한 연산 방지)
        long twentyFourHoursAgo = Instant.now().minusSeconds(24 * 60 * 60).toEpochMilli();
        if (timestamp.toEpochMilli() < twentyFourHoursAgo) {
            log.debug("Skipping realtime metrics for old data: timestamp={}", timestamp);
            return;
        }

        // Sorted Set에 앱 사용 기록 저장 (timestamp를 score로 사용)
        String eventsKey = realtimeKey + ":events";
        String member = packageName + ":" + appName + ":" + categoryId + ":" + usageMinutes;
        redisTemplate.opsForZSet().add(eventsKey, member, (double) timestamp.toEpochMilli());

        // 24시간 이전 데이터 삭제 (안전장치)
        redisTemplate.opsForZSet().removeRangeByScore(eventsKey, 0, twentyFourHoursAgo);

        // 실시간 통계 갱신
        updateRealtimeStats(realtimeKey);

        redisTemplate.expire(eventsKey, 48, TimeUnit.HOURS);
    }

    /**
     * 실시간 통계 계산 (Top 3 앱, 카테고리 비율)
     */
    private void updateRealtimeStats(String realtimeKey) {
        String eventsKey = realtimeKey + ":events";

        long twentyFourHoursAgo = Instant.now().minusSeconds(24 * 60 * 60).toEpochMilli();
        Set<Object> events = redisTemplate.opsForZSet().rangeByScore(eventsKey,
                twentyFourHoursAgo, Double.MAX_VALUE);

        if (events == null || events.isEmpty()) {
            return;
        }

        Map<String, Integer> appUsage = new HashMap<>();
        Map<String, String> packageToAppName = new HashMap<>();
        Map<Integer, Integer> categoryUsage = new HashMap<>();
        int totalMinutes = 0;

        // 이벤트 분석
        for (Object event : events) {
            String[] parts = event.toString().split(":");
            if (parts.length < 4) continue;

            String packageName = parts[0];
            String appName = parts[1];
            int categoryId = Integer.parseInt(parts[2]);
            int minutes = Integer.parseInt(parts[3]);

            appUsage.merge(packageName, minutes, Integer::sum);
            packageToAppName.put(packageName, appName);
            categoryUsage.merge(categoryId, minutes, Integer::sum);
            totalMinutes += minutes;
        }

        // Top 3 앱 저장
        List<Map.Entry<String, Integer>> topApps = appUsage.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(3)
                .toList();

        String statsKey = realtimeKey + ":stats";
        redisTemplate.delete(statsKey);

        for (int i = 0; i < topApps.size(); i++) {
            Map.Entry<String, Integer> entry = topApps.get(i);
            String packageName = entry.getKey();
            String appName = packageToAppName.get(packageName);
            redisTemplate.opsForHash().put(statsKey, "top_app_" + (i + 1) + "_package_name", packageName);
            redisTemplate.opsForHash().put(statsKey, "top_app_" + (i + 1) + "_app_name", appName);
            redisTemplate.opsForHash().put(statsKey, "top_app_" + (i + 1) + "_minutes", entry.getValue());
        }

        // 카테고리 비율 저장
        if (totalMinutes > 0) {
            for (Map.Entry<Integer, Integer> entry : categoryUsage.entrySet()) {
                float ratio = (float) entry.getValue() / totalMinutes;
                String categoryName = categoryMappingService.getCategoryName(entry.getKey());
                redisTemplate.opsForHash().put(statsKey, categoryName.toLowerCase() + "_ratio", ratio);
            }
        }

        redisTemplate.expire(statsKey, 48, TimeUnit.HOURS);
    }

    /**
     * UTC offset을 고려한 LocalDate 계산
     */
    private LocalDate getLocalDate(Instant timestamp, int utcOffsetHours) {
        ZoneId zoneId = ZoneId.ofOffset("UTC", java.time.ZoneOffset.ofHours(utcOffsetHours));
        return timestamp.atZone(zoneId).toLocalDate();
    }

    /**
     * UTC offset을 고려한 시간(hour) 계산
     */
    private int getHour(Instant timestamp, int utcOffsetHours) {
        ZoneId zoneId = ZoneId.ofOffset("UTC", java.time.ZoneOffset.ofHours(utcOffsetHours));
        return timestamp.atZone(zoneId).getHour();
    }

    private String buildDailyKey(UUID subjectId, LocalDate date) {
        return DAILY_METRICS_PREFIX + subjectId + ":" + date;
    }

    private String buildRealtimeKey(UUID subjectId) {
        return REALTIME_METRICS_PREFIX + subjectId;
    }

    private String buildForegroundKey(UUID subjectId, String packageName) {
        return FOREGROUND_APP_PREFIX + subjectId + ":" + packageName;
    }

    /**
     * 실시간 통계 조회 API용
     */
    public Map<Object, Object> getRealtimeStats(UUID subjectId) {
        String statsKey = buildRealtimeKey(subjectId) + ":stats";
        return redisTemplate.opsForHash().entries(statsKey);
    }

    public List<TopApp> getTop3Apps(UUID subjectId) {
        String eventsKey = buildRealtimeKey(subjectId) + ":events";

        long twentyFourHoursAgo = Instant.now().minusSeconds(24 * 60 * 60).toEpochMilli();
        Set<Object> events = redisTemplate.opsForZSet().rangeByScore(eventsKey,
                twentyFourHoursAgo, Double.MAX_VALUE);

        if (events == null || events.isEmpty()) {
            log.debug("No events found for subjectId={} in last 24 hours", subjectId);
            return Collections.emptyList();
        }

        Map<String, Integer> appUsage = new HashMap<>();
        Map<String, String> packageToAppName = new HashMap<>();

        // 이벤트 분석하여 앱별 사용시간 집계
        for (Object event : events) {
            String[] parts = event.toString().split(":");
            if (parts.length < 4) continue;

            String packageName = parts[0];
            String appName = parts[1];
            int minutes = Integer.parseInt(parts[3]);

            appUsage.merge(packageName, minutes, Integer::sum);
            packageToAppName.put(packageName, appName);
        }

        // Top 3 앱 추출
        List<Map.Entry<String, Integer>> sortedApps = appUsage.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(3)
                .toList();

        List<TopApp> topApps = new ArrayList<>();
        for (int i = 0; i < sortedApps.size(); i++) {
            Map.Entry<String, Integer> entry = sortedApps.get(i);
            String packageName = entry.getKey();
            String appName = packageToAppName.get(packageName);
            topApps.add(new TopApp(i + 1, appName, packageName, entry.getValue()));
        }

        log.debug("Top 3 apps calculated for subjectId={}: {} apps found", subjectId, topApps.size());

        return topApps;
    }

    /**
     * 24시간 카테고리 사용 비율 조회 (상위 4개 + 기타)
     */
    public List<CategoryUsageResponse> getCategoryUsage(UUID subjectId) {
        String eventsKey = buildRealtimeKey(subjectId) + ":events";

        long twentyFourHoursAgo = Instant.now().minusSeconds(24 * 60 * 60).toEpochMilli();
        Set<Object> events = redisTemplate.opsForZSet().rangeByScore(eventsKey,
                twentyFourHoursAgo, Double.MAX_VALUE);

        if (events == null || events.isEmpty()) {
            log.debug("No events found for subjectId={} in last 24 hours", subjectId);
            return Collections.emptyList();
        }

        Map<Integer, Integer> categoryUsage = new HashMap<>();
        int totalMinutes = 0;

        // 이벤트 분석하여 카테고리별 사용시간 집계
        for (Object event : events) {
            String[] parts = event.toString().split(":");
            if (parts.length < 4) continue;

            int categoryId = Integer.parseInt(parts[2]);
            int minutes = Integer.parseInt(parts[3]);

            categoryUsage.merge(categoryId, minutes, Integer::sum);
            totalMinutes += minutes;
        }

        if (totalMinutes == 0) {
            log.debug("Total usage is 0 for subjectId={}", subjectId);
            return Collections.emptyList();
        }

        // 카테고리별 비율 계산 및 정렬 (사용량 내림차순)
        List<Map.Entry<Integer, Integer>> sortedCategories = categoryUsage.entrySet().stream()
                .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed())
                .toList();

        List<CategoryUsageResponse> result = new ArrayList<>();
        int othersMinutes = 0;

        // 상위 4개 카테고리 추출
        for (int i = 0; i < sortedCategories.size(); i++) {
            Map.Entry<Integer, Integer> entry = sortedCategories.get(i);
            int categoryId = entry.getKey();
            int minutes = entry.getValue();
            float ratio = (float) minutes / totalMinutes;

            if (i < 4) {
                // 상위 4개는 개별 항목으로 추가
                String categoryName = categoryMappingService.getCategoryName(categoryId);
                result.add(CategoryUsageResponse.builder()
                        .categoryName(categoryName)
                        .ratio(ratio)
                        .build());
            } else {
                // 5번째부터는 "기타"로 합산
                othersMinutes += minutes;
            }
        }

        // "기타" 카테고리 추가 (5개 이상의 카테고리가 있는 경우)
        if (othersMinutes > 0) {
            float othersRatio = (float) othersMinutes / totalMinutes;
            result.add(CategoryUsageResponse.builder()
                    .categoryName("OTHERS")
                    .ratio(othersRatio)
                    .build());
        }

        log.debug("Category usage calculated for subjectId={}: {} categories, {} in others",
                subjectId, result.size(), othersMinutes);

        return result;
    }

    /**
     * 마지막 세션 종료 시간 저장 (세션 병합용)
     */
    private void saveLastSession(UUID subjectId, String packageName, long endTimestamp) {
        String key = buildLastSessionKey(subjectId, packageName);
        redisTemplate.opsForValue().set(key, endTimestamp, 10, TimeUnit.SECONDS);
    }

    /**
     * 마지막 세션 종료 시간 조회
     */
    private Long getLastSessionEnd(UUID subjectId, String packageName) {
        String key = buildLastSessionKey(subjectId, packageName);
        Object value = redisTemplate.opsForValue().get(key);
        return value != null ? Long.parseLong(value.toString()) : null;
    }

    /**
     * 자정 넘김을 고려한 사용시간 집계
     */
    private void aggregateUsageTimeWithDateBoundary(RawData rawData, Instant startTime,
                                                     Instant endTime, long usageMillis, int utcOffset) {
        LocalDate startDate = getLocalDate(startTime, utcOffset);
        LocalDate endDate = getLocalDate(endTime, utcOffset);

        // 같은 날이면 기존 로직 사용
        if (startDate.equals(endDate)) {
            int usageMinutes = (int) (usageMillis / 60000);
            aggregateUsageTime(rawData, startTime, usageMinutes, utcOffset);
            return;
        }

        // 자정 넘김: 날짜별로 분할 집계
        log.info("Multi-day session detected: subjectId={}, package={}, start={}, end={}",
                rawData.getSubjectId(), rawData.getPackageName(), startDate, endDate);

        ZoneId zoneId = ZoneId.ofOffset("UTC", java.time.ZoneOffset.ofHours(utcOffset));
        LocalDate currentDate = startDate;

        while (!currentDate.isAfter(endDate)) {
            Instant dayStart = currentDate.equals(startDate) ? startTime :
                    currentDate.atStartOfDay(zoneId).toInstant();
            Instant dayEnd = currentDate.equals(endDate) ? endTime :
                    currentDate.plusDays(1).atStartOfDay(zoneId).toInstant();

            long dayUsageMillis = dayEnd.toEpochMilli() - dayStart.toEpochMilli();
            int dayUsageMinutes = (int) (dayUsageMillis / 60000);

            // 각 날짜별로 집계
            aggregateUsageTimeForDate(rawData, dayStart, dayUsageMinutes, utcOffset, currentDate);

            currentDate = currentDate.plusDays(1);
        }
    }

    /**
     * 특정 날짜에 대한 사용시간 집계
     */
    private void aggregateUsageTimeForDate(RawData rawData, Instant startTime,
                                            int usageMinutes, int utcOffset, LocalDate targetDate) {
        UUID subjectId = rawData.getSubjectId();
        String packageName = rawData.getPackageName();

        String dailyKey = buildDailyKey(subjectId, targetDate);
        String realtimeKey = buildRealtimeKey(subjectId);

        // 카테고리 정보
        int categoryId = categoryMappingService.getCategoryId(packageName);
        boolean isSocial = categoryMappingService.isSocialCategory(categoryId);

        // 1. 총 사용시간
        redisTemplate.opsForHash().increment(dailyKey, "total_usage_minutes", usageMinutes);

        // 2. 시간대별 사용시간 (시작 시간 기준)
        aggregateByTimeOfDay(dailyKey, startTime, usageMinutes, utcOffset, isSocial);

        // 3. 시간별 사용량 (most_active_hour 계산용)
        int hour = getHour(startTime, utcOffset);
        redisTemplate.opsForHash().increment(dailyKey, "hour_" + hour, usageMinutes);

        // 4. 카테고리별 사용시간
        redisTemplate.opsForHash().increment(dailyKey, "category_" + categoryId, usageMinutes);

        // 5. 앱별 사용시간 (Top 3 계산용)
        redisTemplate.opsForHash().increment(dailyKey, "app:" + packageName + ":" + rawData.getName(), usageMinutes);

        // 6. 실시간 메트릭 (24시간 슬라이딩 윈도우)
        aggregateRealtimeMetrics(realtimeKey, packageName, rawData.getName(), startTime, usageMinutes, categoryId);

        // TTL 설정 (48시간 후 자동 삭제)
        redisTemplate.expire(dailyKey, 48, TimeUnit.HOURS);

        log.debug("Usage aggregated for date {}: subjectId={}, package={}, minutes={}",
                targetDate, subjectId, packageName, usageMinutes);
    }

    private String buildLastSessionKey(UUID subjectId, String packageName) {
        return LAST_SESSION_PREFIX + subjectId + ":" + packageName;
    }

    /**
     * 모든 활성 세션을 강제 종료하고 집계 (일일 배치용)
     */
    public void closeAllActiveSessions(UUID subjectId, Instant endTime, int utcOffset) {
        String pattern = FOREGROUND_APP_PREFIX + subjectId + ":*";
        Set<String> keys = redisTemplate.keys(pattern);

        if (keys.isEmpty()) {
            log.debug("No active sessions found for subjectId={}", subjectId);
            return;
        }

        log.info("Closing {} active sessions for subjectId={} at {}", keys.size(), subjectId, endTime);

        for (String key : keys) {
            Map<Object, Object> foregroundInfo = redisTemplate.opsForHash().entries(key);
            if (foregroundInfo.isEmpty()) continue;

            // 패키지명 추출 (key format: foreground_app:subjectId:packageName)
            String packageName = key.substring(key.lastIndexOf(":") + 1);

            long startTimestamp = Long.parseLong(foregroundInfo.get("startTimestamp").toString());
            long endTimestamp = endTime.toEpochMilli();
            long usageMillis = endTimestamp - startTimestamp;

            // 비정상적인 시간이거나 너무 짧은 세션은 무시
            if (usageMillis < MIN_USAGE_MILLIS || usageMillis > 24 * 60 * 60 * 1000L) {
                log.warn("Skipping abnormal session: package={}, duration={}ms", packageName, usageMillis);
                redisTemplate.delete(key);
                continue;
            }

            // RawData 임시 객체 생성 (집계용)
            RawData rawData = RawData.builder()
                    .subjectId(subjectId)
                    .packageName(packageName)
                    .timestamp(endTime)
                    .utcoffset(utcOffset)
                    .build();

            // 사용시간 집계
            aggregateUsageTimeWithDateBoundary(rawData, Instant.ofEpochMilli(startTimestamp),
                    endTime, usageMillis, utcOffset);

            log.info("Closed active session: subjectId={}, package={}, minutes={}",
                    subjectId, packageName, usageMillis / 60000);

            // 세션 삭제
            redisTemplate.delete(key);
        }
    }

    public TodayUsageSummary getTodayUsage(UUID subjectId, int utcOffset) {
        LocalDate today = getLocalDate(Instant.now(), utcOffset);
        String dailyKey = buildDailyKey(subjectId, today);
        Map<Object, Object> dailyMetrics = redisTemplate.opsForHash().entries(dailyKey);

        int dawnMinutes = dailyMetrics.get("dawn_minutes") != null ? Integer.parseInt(dailyMetrics.get("dawn_minutes").toString()) : 0;
        int morningMinutes = dailyMetrics.get("morning_minutes") != null ? Integer.parseInt(dailyMetrics.get("morning_minutes").toString()) : 0;
        int afternoonMinutes = dailyMetrics.get("afternoon_minutes") != null ? Integer.parseInt(dailyMetrics.get("afternoon_minutes").toString()) : 0;
        int eveningMinutes = dailyMetrics.get("evening_minutes") != null ? Integer.parseInt(dailyMetrics.get("evening_minutes").toString()) : 0;

        int[] hourlyMinutes = new int[24];
        for (int hour = 0; hour < 24; hour++) {
            Object usage = dailyMetrics.get("hour_" + hour);
            hourlyMinutes[hour] = usage != null ? Integer.parseInt(usage.toString()) : 0;
        }

        int maxTwoHourUsage = 0;  // Changed from -1: only update when there's actual usage
        int mostActiveHourStart = -1;

        for (int i = 0; i < 23; i++) {
            int twoHourUsage = hourlyMinutes[i] + hourlyMinutes[i+1];
            if (twoHourUsage > maxTwoHourUsage) {
                maxTwoHourUsage = twoHourUsage;
                mostActiveHourStart = i;
            }
        }

        DailyUsageSummary dailySummary = DailyUsageSummary.builder()
                .date(today)
                .dawnMinutes(dawnMinutes)
                .morningMinutes(morningMinutes)
                .afternoonMinutes(afternoonMinutes)
                .eveningMinutes(eveningMinutes)
                .build();

        return TodayUsageSummary.builder()
                .dailySummary(dailySummary)
                .mostActiveHourStart(mostActiveHourStart)
                .build();
    }
}
