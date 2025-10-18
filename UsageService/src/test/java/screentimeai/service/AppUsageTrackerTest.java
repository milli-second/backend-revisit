package screentimeai.service;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import screentimeai.domain.entity.RawData;
import screentimeai.application.AppUsageTracker;
import screentimeai.application.CategoryMappingService;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class AppUsageTrackerTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private CategoryMappingService categoryMappingService;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private ZSetOperations<String, Object> zSetOperations;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private AppUsageTracker appUsageTracker;

    private UUID testSubjectId;
    private Instant testTimestamp;

    @BeforeEach
    void setUp() {
        testSubjectId = UUID.randomUUID();
        testTimestamp = Instant.now();

        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.expire(anyString(), anyLong(), any())).thenReturn(true);
        lenient().when(categoryMappingService.getCategoryId(anyString()))
                .thenReturn(CategoryMappingService.CATEGORY_SOCIAL_COMM);
        lenient().when(categoryMappingService.isSocialCategory(anyInt())).thenReturn(true);
    }

    @Test
    @DisplayName("MOVE_TO_FOREGROUND 이벤트 처리 - Redis에 시작 정보 저장")
    void testHandleForegroundEvent() {
        // Given
        RawData rawData = createRawData("MOVE_TO_FOREGROUND", "com.example.app");

        // When
        appUsageTracker.processEvent(rawData);

        // Then
        String expectedKey = "foreground_app:" + testSubjectId + ":com.example.app";
        verify(hashOperations, times(1)).putAll(eq(expectedKey), any(Map.class));
        verify(redisTemplate, times(1)).expire(eq(expectedKey), eq(30L), any());
    }

    @Test
    @DisplayName("MOVE_TO_BACKGROUND 이벤트 처리 - 사용시간 계산 및 집계")
    void testHandleBackgroundEvent() {
        // Given
        Instant startTime = testTimestamp.minus(30, ChronoUnit.MINUTES);
        String packageName = "com.example.app";

        // Foreground 정보 모킹
        Map<Object, Object> foregroundInfo = new HashMap<>();
        foregroundInfo.put("startTimestamp", String.valueOf(startTime.toEpochMilli()));
        foregroundInfo.put("utcOffset", "9");
        foregroundInfo.put("lastHeartbeat", String.valueOf(startTime.toEpochMilli()));

        when(hashOperations.entries(anyString())).thenReturn(foregroundInfo);

        RawData rawData = createRawData("MOVE_TO_BACKGROUND", packageName);

        // When
        appUsageTracker.processEvent(rawData);

        // Then
        String expectedKey = "foreground_app:" + testSubjectId + ":com.example.app";
        verify(hashOperations, times(1)).entries(expectedKey);
        verify(redisTemplate, times(1)).delete(expectedKey);

        // 사용시간이 집계되었는지 확인 (총 사용시간, 시간대별, 카테고리별 등)
        verify(hashOperations, atLeastOnce()).increment(anyString(), eq("total_usage_minutes"), anyLong());
    }

    @Test
    @DisplayName("비정상 사용시간 필터링 - 음수 시간")
    void testAbnormalUsageTimeNegative() {
        // Given
        Instant startTime = testTimestamp.plus(1, ChronoUnit.HOURS); // 미래 시간
        String packageName = "com.example.app";

        Map<Object, Object> foregroundInfo = new HashMap<>();
        foregroundInfo.put("startTimestamp", String.valueOf(startTime.toEpochMilli()));
        foregroundInfo.put("utcOffset", "9");
        foregroundInfo.put("lastHeartbeat", String.valueOf(startTime.toEpochMilli()));

        when(hashOperations.entries(anyString())).thenReturn(foregroundInfo);

        RawData rawData = createRawData("MOVE_TO_BACKGROUND", packageName);

        // When
        appUsageTracker.processEvent(rawData);

        // Then
        String expectedKey = "foreground_app:" + testSubjectId + ":com.example.app";
        verify(redisTemplate, times(1)).delete(expectedKey);
        // 사용시간 집계가 일어나지 않아야 함
        verify(hashOperations, never()).increment(anyString(), eq("total_usage_minutes"), anyLong());
    }

    @Test
    @DisplayName("비정상 사용시간 필터링 - 24시간 초과")
    void testAbnormalUsageTimeOver24Hours() {
        // Given
        Instant startTime = testTimestamp.minus(25, ChronoUnit.HOURS);
        String packageName = "com.example.app";

        Map<Object, Object> foregroundInfo = new HashMap<>();
        foregroundInfo.put("startTimestamp", String.valueOf(startTime.toEpochMilli()));
        foregroundInfo.put("utcOffset", "9");
        foregroundInfo.put("lastHeartbeat", String.valueOf(startTime.toEpochMilli()));

        when(hashOperations.entries(anyString())).thenReturn(foregroundInfo);

        RawData rawData = createRawData("MOVE_TO_BACKGROUND", packageName);

        // When
        appUsageTracker.processEvent(rawData);

        // Then
        String expectedKey = "foreground_app:" + testSubjectId + ":com.example.app";
        verify(redisTemplate, times(1)).delete(expectedKey);
        // 사용시간 집계가 일어나지 않아야 함
        verify(hashOperations, never()).increment(anyString(), eq("total_usage_minutes"), anyLong());
    }

    @Test
    @DisplayName("자정 경계 케이스 - UTC offset 적용")
    void testMidnightBoundaryWithUtcOffset() {
        // Given: 한국 시간 23:30 (UTC+9)
        Instant koreaTime2330 = Instant.parse("2025-10-10T14:30:00Z"); // UTC 14:30 = KST 23:30
        Instant koreaTime0030 = Instant.parse("2025-10-10T15:30:00Z"); // UTC 15:30 = KST 00:30

        String packageName = "com.example.app";

        // 23:30에 시작
        Map<Object, Object> foregroundInfo = new HashMap<>();
        foregroundInfo.put("startTimestamp", String.valueOf(koreaTime2330.toEpochMilli()));
        foregroundInfo.put("utcOffset", "9");
        foregroundInfo.put("lastHeartbeat", String.valueOf(koreaTime2330.toEpochMilli()));

        when(hashOperations.entries(anyString())).thenReturn(foregroundInfo);

        // 00:30에 종료
        RawData rawData = RawData.builder()
                .subjectId(testSubjectId)
                .timestamp(koreaTime0030)
                .utcoffset(9)
                .packageName(packageName)
                .type("MOVE_TO_BACKGROUND")
                .build();

        // When
        appUsageTracker.processEvent(rawData);

        // Then
        // 2025-10-10과 2025-10-11에 각각 집계되어야 함
        verify(hashOperations, atLeastOnce()).increment(anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("24시간 슬라이딩 윈도우 - 오래된 데이터 삭제")
    void testRealtimeMetricsSlidingWindow() {
        // Given
        RawData rawData = createRawData("MOVE_TO_BACKGROUND", "com.example.app");

        Instant startTime = testTimestamp.minus(30, ChronoUnit.MINUTES);
        Map<Object, Object> foregroundInfo = new HashMap<>();
        foregroundInfo.put("startTimestamp", String.valueOf(startTime.toEpochMilli()));
        foregroundInfo.put("utcOffset", "9");
        foregroundInfo.put("lastHeartbeat", String.valueOf(startTime.toEpochMilli()));

        when(hashOperations.entries(anyString())).thenReturn(foregroundInfo);

        // When
        appUsageTracker.processEvent(rawData);

        // Then
        verify(zSetOperations, times(1))
                .removeRangeByScore(anyString(), eq(0.0), anyDouble());
    }

    @Test
    @DisplayName("오늘 사용량 요약 및 가장 활발한 시간대 조회")
    void testGetTodayUsage() {
        // Given
        LocalDate today = LocalDate.now();
        String dailyKey = "daily_metrics:" + testSubjectId + ":" + today;

        Map<Object, Object> dailyMetrics = getObjectObjectMap();


        when(hashOperations.entries(dailyKey)).thenReturn(dailyMetrics);

        // When
        screentimeai.infra.dto.TodayUsageSummary todayUsage = appUsageTracker.getTodayUsage(testSubjectId, 9);

        // Then
        assertNotNull(todayUsage);
        screentimeai.infra.dto.DailyUsageSummary summary = todayUsage.getDailySummary();
        assertNotNull(summary);

        assertEquals(today, summary.getDate());
        assertEquals(0, summary.getDawnMinutes());
        assertEquals(120, summary.getMorningMinutes());
        assertEquals(120, summary.getAfternoonMinutes());
        assertEquals(15, summary.getEveningMinutes());

        // mostActiveHourStart should be 14 (14:00-16:00 is 45+75=120, 8:00-10:00 is 30+60=90)
        assertEquals(14, todayUsage.getMostActiveHourStart());
    }

    @NotNull
    private static Map<Object, Object> getObjectObjectMap() {
        Map<Object, Object> dailyMetrics = new HashMap<>();
        dailyMetrics.put("hour_8", "30");
        dailyMetrics.put("hour_9", "60");
        dailyMetrics.put("hour_10", "30");
        dailyMetrics.put("hour_14", "45");
        dailyMetrics.put("hour_15", "75");
        dailyMetrics.put("hour_22", "15");

        dailyMetrics.put("dawn_minutes", "0");
        dailyMetrics.put("morning_minutes", "120");
        dailyMetrics.put("afternoon_minutes", "120");
        dailyMetrics.put("evening_minutes", "15");
        return dailyMetrics;
    }

    private RawData createRawData(String type, String packageName) {
        return RawData.builder()
                .subjectId(testSubjectId)
                .timestamp(testTimestamp)
                .utcoffset(9)
                .sourceType("PHONE")
                .sourceInfo("test-device")
                .packageName(packageName)
                .name("Test App")
                .isSystemApp(0)
                .isUpdatedSystemApp(0)
                .type(type)
                .build();
    }
}
