package screentimeai.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import screentimeai.application.AppUsageTracker;
import screentimeai.application.DailyMetricsAggregator;
import screentimeai.domain.entity.DailyUserMetrics;
import screentimeai.domain.entity.RawData;
import screentimeai.infra.repository.DailyUserMetricsRepository;
import screentimeai.infra.repository.RawDataRepository;
import screentimeai.presentation.dto.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DailyMetricsAggregatorTest {

    @Mock
    private AppUsageTracker appUsageTracker;

    @Mock
    private DailyUserMetricsRepository dailyUserMetricsRepository;

    @Mock
    private RawDataRepository rawDataRepository;

    @InjectMocks
    private DailyMetricsAggregator dailyMetricsAggregator;

    private UUID testSubjectId;
    private RawData testRawData;

    @BeforeEach
    void setUp() {
        testSubjectId = UUID.randomUUID();
        testRawData = createTestRawData("MOVE_TO_FOREGROUND", "com.test.app");
    }

    @Test
    @DisplayName("단일 RawData 집계 - AppUsageTracker에 위임")
    void testAggregateRawData() {
        // Given
        doNothing().when(appUsageTracker).processEvent(any(RawData.class));

        // When
        dailyMetricsAggregator.aggregateRawData(testRawData);

        // Then
        verify(appUsageTracker, times(1)).processEvent(testRawData);
    }

    @Test
    @DisplayName("RawData 배치 집계 - 모든 이벤트가 처리되는지 확인")
    void testAggregateRawDataBatch() {
        // Given
        List<RawData> rawDataList = Arrays.asList(
                createTestRawData("MOVE_TO_FOREGROUND", "com.app1"),
                createTestRawData("MOVE_TO_BACKGROUND", "com.app1"),
                createTestRawData("MOVE_TO_FOREGROUND", "com.app2")
        );

        doNothing().when(appUsageTracker).processEvent(any(RawData.class));

        // When
        dailyMetricsAggregator.aggregateRawDataBatch(rawDataList);

        // Then
        verify(appUsageTracker, times(3)).processEvent(any(RawData.class));

        // 각 RawData가 정확히 전달되었는지 확인
        ArgumentCaptor<RawData> rawDataCaptor = ArgumentCaptor.forClass(RawData.class);
        verify(appUsageTracker, times(3)).processEvent(rawDataCaptor.capture());

        List<RawData> capturedRawData = rawDataCaptor.getAllValues();
        assertThat(capturedRawData).hasSize(3);
        assertThat(capturedRawData.get(0).getPackageName()).isEqualTo("com.app1");
        assertThat(capturedRawData.get(1).getPackageName()).isEqualTo("com.app1");
        assertThat(capturedRawData.get(2).getPackageName()).isEqualTo("com.app2");
    }

    @Test
    @DisplayName("RawData 집계 실패 시 예외 전파")
    void testAggregateRawDataFailure() {
        // Given
        RuntimeException expectedException = new RuntimeException("Redis error");
        doThrow(expectedException).when(appUsageTracker).processEvent(any(RawData.class));

        // When & Then
        RuntimeException thrownException = assertThrows(RuntimeException.class, () -> {
            dailyMetricsAggregator.aggregateRawData(testRawData);
        });

        assertThat(thrownException).isSameAs(expectedException);
        verify(appUsageTracker, times(1)).processEvent(testRawData);
    }

    @Test
    @DisplayName("배치 집계 중 일부 실패 시 예외 전파")
    void testAggregateRawDataBatchPartialFailure() {
        // Given
        List<RawData> rawDataList = Arrays.asList(
                createTestRawData("MOVE_TO_FOREGROUND", "com.app1"),
                createTestRawData("MOVE_TO_BACKGROUND", "com.app1")
        );

        // 첫 번째는 성공, 두 번째에서 실패
        doNothing().doThrow(new RuntimeException("Processing error"))
                .when(appUsageTracker).processEvent(any(RawData.class));

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            dailyMetricsAggregator.aggregateRawDataBatch(rawDataList);
        });

        // 첫 번째 이벤트는 처리되었지만, 예외로 인해 배치가 중단됨
        verify(appUsageTracker, times(2)).processEvent(any(RawData.class));
    }

    @Test
    @DisplayName("실시간 통계 조회 - AppUsageTracker에 위임")
    void testGetRealtimeStats() {
        // Given
        Map<Object, Object> expectedStats = new HashMap<>();
        expectedStats.put("total_usage_minutes", "120");
        expectedStats.put("current_session_minutes", "30");

        when(appUsageTracker.getRealtimeStats(testSubjectId)).thenReturn(expectedStats);

        // When
        Map<Object, Object> result = dailyMetricsAggregator.getRealtimeStats(testSubjectId);

        // Then
        assertThat(result).isEqualTo(expectedStats);
        assertThat(result.get("total_usage_minutes")).isEqualTo("120");
        verify(appUsageTracker, times(1)).getRealtimeStats(testSubjectId);
    }

    @Test
    @DisplayName("주간 사용 패턴 조회 - 7일치 데이터 반환")
    void testGetWeeklyUsagePattern() {
        // Given
        LocalDate today = LocalDate.now();
        LocalDate endDate = today.minusDays(1);  // 어제
        LocalDate startDate = endDate.minusDays(6);  // 7일 전

        List<DailyUserMetrics> mockMetrics = Arrays.asList(
                createDailyUserMetrics(endDate, 120),           // 어제
                createDailyUserMetrics(endDate.minusDays(1), 90),   // 2일 전
                createDailyUserMetrics(endDate.minusDays(2), 150)   // 3일 전
        );

        when(dailyUserMetricsRepository.findBySubjectIdAndSummaryDateBetweenOrderBySummaryDateDesc(
                testSubjectId, startDate, endDate)).thenReturn(mockMetrics);

        // When
        List<DailyUsageResponse> result = dailyMetricsAggregator.getWeeklyUsagePattern(testSubjectId);

        // Then
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getDate()).isEqualTo(endDate);
        assertThat(result.get(0).getTotalUsageMinutes()).isEqualTo(120);
        assertThat(result.get(1).getDate()).isEqualTo(endDate.minusDays(1));
        assertThat(result.get(1).getTotalUsageMinutes()).isEqualTo(90);

        verify(dailyUserMetricsRepository, times(1))
                .findBySubjectIdAndSummaryDateBetweenOrderBySummaryDateDesc(testSubjectId, startDate, endDate);
    }

    @Test
    @DisplayName("주간 사용 패턴 조회 - 데이터 없을 때 빈 리스트 반환")
    void testGetWeeklyUsagePatternNoData() {
        // Given
        LocalDate today = LocalDate.now();
        LocalDate endDate = today.minusDays(1);
        LocalDate startDate = endDate.minusDays(6);

        when(dailyUserMetricsRepository.findBySubjectIdAndSummaryDateBetweenOrderBySummaryDateDesc(
                testSubjectId, startDate, endDate)).thenReturn(Collections.emptyList());

        // When
        List<DailyUsageResponse> result = dailyMetricsAggregator.getWeeklyUsagePattern(testSubjectId);

        // Then
        assertThat(result).isEmpty();
        verify(dailyUserMetricsRepository, times(1))
                .findBySubjectIdAndSummaryDateBetweenOrderBySummaryDateDesc(testSubjectId, startDate, endDate);
    }

    @Test
    @DisplayName("카테고리 사용량 조회 - AppUsageTracker에 위임")
    void testGetCategoryUsage() {
        // Given
        List<CategoryUsageResponse> expectedUsage = Arrays.asList(
                CategoryUsageResponse.builder()
                        .categoryName("SOCIAL_COMM")
                        .ratio(0.5f)
                        .build(),
                CategoryUsageResponse.builder()
                        .categoryName("GAME")
                        .ratio(0.33f)
                        .build()
        );

        when(appUsageTracker.getCategoryUsage(testSubjectId)).thenReturn(expectedUsage);

        // When
        List<CategoryUsageResponse> result = dailyMetricsAggregator.getCategoryUsage(testSubjectId);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCategoryName()).isEqualTo("SOCIAL_COMM");
        assertThat(result.get(0).getRatio()).isEqualTo(0.5f);

        verify(appUsageTracker, times(1)).getCategoryUsage(testSubjectId);
    }

    @Test
    @DisplayName("Top3 앱 조회 - AppUsageTracker에 위임")
    void testGetTop3Apps() {
        // Given
        List<TopApp> expectedTopApps = Arrays.asList(
                TopApp.builder()
                        .rank(1)
                        .packageName("com.app1")
                        .appName("App 1")
                        .usageMinutes(60)
                        .build(),
                TopApp.builder()
                        .rank(2)
                        .packageName("com.app2")
                        .appName("App 2")
                        .usageMinutes(40)
                        .build()
        );

        when(appUsageTracker.getTop3Apps(testSubjectId)).thenReturn(expectedTopApps);

        // When
        List<TopApp> result = dailyMetricsAggregator.getTop3Apps(testSubjectId);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getPackageName()).isEqualTo("com.app1");
        assertThat(result.get(0).getUsageMinutes()).isEqualTo(60);

        verify(appUsageTracker, times(1)).getTop3Apps(testSubjectId);
    }

    @Test
    @DisplayName("3일 사용량 조회 - UTC offset 기본값 사용")
    void testGetThreeDayUsageWithDefaultUtcOffset() {
        // Given
        when(rawDataRepository.findFirstBySubjectIdOrderByTimestampDesc(testSubjectId))
                .thenReturn(null); // 최근 RawData 없음

        TodayUsageSummary mockTodayUsage = createMockTodayUsage();
        when(appUsageTracker.getTodayUsage(testSubjectId, 9)).thenReturn(mockTodayUsage);

        LocalDate today = LocalDate.now(java.time.ZoneId.ofOffset("UTC", java.time.ZoneOffset.ofHours(9)));
        LocalDate yesterday = today.minusDays(1);
        LocalDate dayBeforeYesterday = today.minusDays(2);

        List<DailyUserMetrics> pastMetrics = Arrays.asList(
                createDailyUserMetrics(yesterday, 100),
                createDailyUserMetrics(dayBeforeYesterday, 80)
        );

        when(dailyUserMetricsRepository.findBySubjectIdAndSummaryDateBetweenOrderBySummaryDateDesc(
                testSubjectId, dayBeforeYesterday, yesterday)).thenReturn(pastMetrics);

        // When
        ThreeDayUsageResponse result = dailyMetricsAggregator.getThreeDayUsage(testSubjectId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getDailySummaries()).hasSize(3);
        assertThat(result.getMostActiveHourStart()).isEqualTo(14);

        // 날짜 순서 확인 (최신순)
        List<DailyUsageSummary> summaries = result.getDailySummaries();
        assertThat(summaries.get(0).getDate()).isEqualTo(today);
        assertThat(summaries.get(1).getDate()).isEqualTo(yesterday);
        assertThat(summaries.get(2).getDate()).isEqualTo(dayBeforeYesterday);

        verify(appUsageTracker, times(1)).getTodayUsage(testSubjectId, 9);
    }

    @Test
    @DisplayName("3일 사용량 조회 - RawData에서 UTC offset 추출")
    void testGetThreeDayUsageWithRawDataUtcOffset() {
        // Given
        RawData latestRawData = createTestRawData("MOVE_TO_BACKGROUND", "com.test.app");
        latestRawData = RawData.builder()
                .subjectId(testSubjectId)
                .timestamp(Instant.now())
                .utcoffset(5) // UTC+5
                .packageName("com.test.app")
                .type("MOVE_TO_BACKGROUND")
                .build();

        when(rawDataRepository.findFirstBySubjectIdOrderByTimestampDesc(testSubjectId))
                .thenReturn(latestRawData);

        TodayUsageSummary mockTodayUsage = createMockTodayUsage();
        when(appUsageTracker.getTodayUsage(testSubjectId, 5)).thenReturn(mockTodayUsage);

        LocalDate today = LocalDate.now(java.time.ZoneId.ofOffset("UTC", java.time.ZoneOffset.ofHours(5)));
        LocalDate yesterday = today.minusDays(1);
        LocalDate dayBeforeYesterday = today.minusDays(2);

        when(dailyUserMetricsRepository.findBySubjectIdAndSummaryDateBetweenOrderBySummaryDateDesc(
                testSubjectId, dayBeforeYesterday, yesterday)).thenReturn(Collections.emptyList());

        // When
        ThreeDayUsageResponse result = dailyMetricsAggregator.getThreeDayUsage(testSubjectId);

        // Then
        assertThat(result).isNotNull();
        verify(appUsageTracker, times(1)).getTodayUsage(testSubjectId, 5);
    }

    private RawData createTestRawData(String type, String packageName) {
        return RawData.builder()
                .subjectId(testSubjectId)
                .timestamp(Instant.now())
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

    private DailyUserMetrics createDailyUserMetrics(LocalDate date, int totalUsage) {
        return DailyUserMetrics.builder()
                .subjectId(testSubjectId)
                .summaryDate(date)
                .totalUsageMinutes(totalUsage)
                .dawnMinutes(10)
                .morningMinutes(30)
                .afternoonMinutes(40)
                .eveningMinutes(20)
                .build();
    }

    private TodayUsageSummary createMockTodayUsage() {
        DailyUsageSummary dailySummary = DailyUsageSummary.builder()
                .date(LocalDate.now())
                .dawnMinutes(10)
                .morningMinutes(30)
                .afternoonMinutes(40)
                .eveningMinutes(20)
                .build();

        return TodayUsageSummary.builder()
                .dailySummary(dailySummary)
                .mostActiveHourStart(14)
                .build();
    }
}