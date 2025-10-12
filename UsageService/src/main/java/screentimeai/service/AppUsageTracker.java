package screentimeai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import screentimeai.domain.RawData;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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

    // Session TTL: 30분 (비정상 종료 감지용)
    private static final long SESSION_TTL_MINUTES = 30;
    // Heartbeat 간격: 5분 (장시간 사용 시 세션 연장용)
    private static final long HEARTBEAT_INTERVAL_MINUTES = 5;

    /**
     * RawData 이벤트를 처리하여 앱 사용시간 집계
     */
    public void processEvent(RawData rawData) {
        String eventType = rawData.getType();
        UUID subjectId = rawData.getSubjectId();

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

        int usageMinutes = (int) (usageMillis / 60000);

        // 사용시간 집계
        aggregateUsageTime(rawData, Instant.ofEpochMilli(startTimestamp),
                rawData.getTimestamp(), usageMinutes, utcOffset);

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
            if (Boolean.TRUE.equals(exists)) {
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
    private void aggregateUsageTime(RawData rawData, Instant startTime, Instant endTime,
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
        redisTemplate.opsForHash().increment(dailyKey, "app:" + packageName, usageMinutes);

        // 6. 실시간 메트릭 (24시간 슬라이딩 윈도우)
        aggregateRealtimeMetrics(realtimeKey, packageName, startTime, usageMinutes, categoryId);

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
    private void aggregateRealtimeMetrics(String realtimeKey, String packageName,
                                           Instant timestamp, int usageMinutes, int categoryId) {
        // Sorted Set에 앱 사용 기록 저장 (timestamp를 score로 사용)
        String eventsKey = realtimeKey + ":events";
        String member = packageName + ":" + categoryId + ":" + usageMinutes;
        redisTemplate.opsForZSet().add(eventsKey, member, (double) timestamp.toEpochMilli());

        // 24시간 이전 데이터 삭제
        long twentyFourHoursAgo = Instant.now().minusSeconds(24 * 60 * 60).toEpochMilli();
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
        Map<Integer, Integer> categoryUsage = new HashMap<>();
        int totalMinutes = 0;

        // 이벤트 분석
        for (Object event : events) {
            String[] parts = event.toString().split(":");
            if (parts.length < 3) continue;

            String packageName = parts[0];
            int categoryId = Integer.parseInt(parts[1]);
            int minutes = Integer.parseInt(parts[2]);

            appUsage.merge(packageName, minutes, Integer::sum);
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
            redisTemplate.opsForHash().put(statsKey, "top_app_" + (i + 1), entry.getKey());
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
}
