package screentimeai.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;

import screentimeai.domain.entity.DailyUserMetrics;
import screentimeai.domain.repository.DailyCategoryUsageRepository;
import screentimeai.domain.repository.DailyUserMetricsRepository;
import screentimeai.domain.entity.DailyCategoryUsage;
import screentimeai.application.DailyMetricsBatchService;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DailyMetricsBatchServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private DailyUserMetricsRepository metricsRepository;

    @Mock
    private DailyCategoryUsageRepository categoryUsageRepository;


    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @InjectMocks
    private DailyMetricsBatchService batchService;

    private UUID testSubjectId;
    private LocalDate testDate;

    @BeforeEach
    void setUp() {
        testSubjectId = UUID.randomUUID();
        testDate = LocalDate.now().minusDays(1);

        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
    }

    @Test
    @DisplayName("배치 작업 - Redis 데이터를 DB로 저장")
    void testSaveDailyMetricsToDB() {
        // Given
        String redisKey = "daily_metrics:" + testSubjectId + ":" + testDate;
        Set<String> keys = Set.of(redisKey);

        Map<Object, Object> redisData = createMockRedisData();

        when(redisTemplate.keys(anyString())).thenReturn(keys);
        when(hashOperations.entries(redisKey)).thenReturn(redisData);

        // When
        batchService.saveDailyMetricsToDB();

        // Then
        verify(metricsRepository, times(1)).save(any(DailyUserMetrics.class));
        verify(categoryUsageRepository, times(1)).saveAll(anyList());
        verify(redisTemplate, times(1)).delete(redisKey);
    }

    @Test
    @DisplayName("DailyUserMetrics 변환 - 총 사용시간 및 시간대별 분")
    void testConvertToDailyUserMetrics() {
        // Given
        String redisKey = "daily_metrics:" + testSubjectId + ":" + testDate;
        Set<String> keys = Set.of(redisKey);

        Map<Object, Object> redisData = new HashMap<>();
        redisData.put("total_usage_minutes", "120");
        redisData.put("dawn_minutes", "30");
        redisData.put("morning_minutes", "40");
        redisData.put("afternoon_minutes", "30");
        redisData.put("evening_minutes", "20");
        redisData.put("dawn_sns_minutes", "15");
        redisData.put("daily_pickup_count", "50");
        redisData.put("category_1", "30");  // GAME
        redisData.put("category_2", "40");  // SOCIAL_COMM
        redisData.put("category_3", "20");  // LEARNING
        redisData.put("category_4", "30");  // ENTERTAINMENT

        when(redisTemplate.keys(anyString())).thenReturn(keys);
        when(hashOperations.entries(redisKey)).thenReturn(redisData);

        ArgumentCaptor<DailyUserMetrics> metricsCaptor = ArgumentCaptor.forClass(DailyUserMetrics.class);

        // When
        batchService.saveDailyMetricsToDB();

        // Then
        verify(metricsRepository).save(metricsCaptor.capture());

        DailyUserMetrics metrics = metricsCaptor.getValue();
        assertThat(metrics.getSubjectId()).isEqualTo(testSubjectId);
        assertThat(metrics.getSummaryDate()).isEqualTo(testDate);
        assertThat(metrics.getTotalUsageMinutes()).isEqualTo(120);
        assertThat(metrics.getDawnMinutes()).isEqualTo(30);
        assertThat(metrics.getMorningMinutes()).isEqualTo(40);
        assertThat(metrics.getAfternoonMinutes()).isEqualTo(30);
        assertThat(metrics.getEveningMinutes()).isEqualTo(20);
        assertThat(metrics.getDawnSnsMinutes()).isEqualTo(15);
        assertThat(metrics.getDailyPickupCount()).isEqualTo(50);

        // 비율 확인
        assertThat(metrics.getGameRatio()).isEqualTo(30f / 120f);
        assertThat(metrics.getSocialCommRatio()).isEqualTo(40f / 120f);
        assertThat(metrics.getLearningRatio()).isEqualTo(20f / 120f);
        assertThat(metrics.getEntertainmentRatio()).isEqualTo(30f / 120f);
    }

    @Test
    @DisplayName("DailyCategoryUsage 추출 - 카테고리별 사용시간")
    void testExtractCategoryUsages() {
        // Given
        String redisKey = "daily_metrics:" + testSubjectId + ":" + testDate;
        Set<String> keys = Set.of(redisKey);

        Map<Object, Object> redisData = new HashMap<>();
        redisData.put("total_usage_minutes", "100");
        redisData.put("category_1", "30");  // GAME
        redisData.put("category_2", "40");  // SOCIAL_COMM
        redisData.put("category_3", "0");   // LEARNING (0분은 저장 안 함)
        redisData.put("category_4", "30");  // ENTERTAINMENT

        when(redisTemplate.keys(anyString())).thenReturn(keys);
        when(hashOperations.entries(redisKey)).thenReturn(redisData);

        ArgumentCaptor<List<DailyCategoryUsage>> categoryCaptor = ArgumentCaptor.forClass(List.class);

        // When
        batchService.saveDailyMetricsToDB();

        // Then
        verify(categoryUsageRepository).saveAll(categoryCaptor.capture());

        List<DailyCategoryUsage> categoryUsages = categoryCaptor.getValue();
        assertThat(categoryUsages).hasSize(3);  // category_3는 0분이므로 제외

        // GAME 카테고리 확인
        DailyCategoryUsage gameUsage = categoryUsages.stream()
                .filter(c -> c.getCategoryId() == 1)
                .findFirst()
                .orElseThrow();
        assertThat(gameUsage.getUsageMinutes()).isEqualTo(30);
    }


    @Test
    @DisplayName("Most Active Hour 계산")
    void testCalculateMostActiveHour() {
        // Given
        String redisKey = "daily_metrics:" + testSubjectId + ":" + testDate;
        Set<String> keys = Set.of(redisKey);

        Map<Object, Object> redisData = new HashMap<>();
        redisData.put("total_usage_minutes", "200");
        // 시간별 사용량
        for (int hour = 0; hour < 24; hour++) {
            redisData.put("hour_" + hour, hour == 14 ? "50" : "10");  // 14시가 가장 활발
        }

        when(redisTemplate.keys(anyString())).thenReturn(keys);
        when(hashOperations.entries(redisKey)).thenReturn(redisData);

        ArgumentCaptor<DailyUserMetrics> metricsCaptor = ArgumentCaptor.forClass(DailyUserMetrics.class);

        // When
        batchService.saveDailyMetricsToDB();

        // Then
        verify(metricsRepository).save(metricsCaptor.capture());

        DailyUserMetrics metrics = metricsCaptor.getValue();
        assertThat(metrics.getMostActiveHourStart()).isEqualTo(14);
    }

    @Test
    @DisplayName("Redis 키 없을 때 정상 종료")
    void testNoRedisKeys() {
        // Given
        when(redisTemplate.keys(anyString())).thenReturn(Collections.emptySet());

        // When
        batchService.saveDailyMetricsToDB();

        // Then
        verify(metricsRepository, never()).save(any());
        verify(categoryUsageRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("부분 실패 허용 - 한 사용자 실패해도 다른 사용자는 저장")
    void testPartialFailure() {
        // Given
        UUID subject1 = UUID.randomUUID();
        UUID subject2 = UUID.randomUUID();

        String key1 = "daily_metrics:" + subject1 + ":" + testDate;
        String key2 = "daily_metrics:" + subject2 + ":" + testDate;

        Set<String> keys = Set.of(key1, key2);
        when(redisTemplate.keys(anyString())).thenReturn(keys);

        // key1은 정상 데이터
        when(hashOperations.entries(key1)).thenReturn(createMockRedisData());

        // key2는 비정상 데이터 (예외 발생)
        when(hashOperations.entries(key2)).thenThrow(new RuntimeException("Redis error"));

        // When
        batchService.saveDailyMetricsToDB();

        // Then
        // 최소 1개는 저장되어야 함 (subject1)
        verify(metricsRepository, atLeastOnce()).save(any(DailyUserMetrics.class));
    }

    private Map<Object, Object> createMockRedisData() {
        Map<Object, Object> data = new HashMap<>();
        data.put("total_usage_minutes", "100");
        data.put("dawn_minutes", "20");
        data.put("morning_minutes", "30");
        data.put("afternoon_minutes", "30");
        data.put("evening_minutes", "20");
        data.put("dawn_sns_minutes", "10");
        data.put("daily_pickup_count", "50");
        data.put("category_1", "30");
        data.put("category_2", "40");
        data.put("category_3", "10");
        data.put("category_4", "20");
        return data;
    }
}
