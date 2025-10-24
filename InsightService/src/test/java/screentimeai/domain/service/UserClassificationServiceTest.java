package screentimeai.domain.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import screentimeai.domain.config.ClassificationThresholds;
import screentimeai.domain.entity.DailyMetricsSaved;
import screentimeai.domain.model.UserClassification;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UserClassificationService 테스트
 *
 * Python 프로젝트의 분류 로직을 검증
 */
class UserClassificationServiceTest {

    private UserClassificationService service;

    @BeforeEach
    void setUp() {
        service = new UserClassificationService(new ClassificationThresholds());
    }

    @Test
    @DisplayName("게임 집중형 사용자 분류 - 높은 게임 비율과 시간")
    void classifyGameFocusedUser() {
        // given: 제공하신 샘플 데이터 (게임 비율 86%, 총 583분)
        DailyMetricsSaved metrics = createMetrics(
                583,  // totalUsageMinutes
                249,  // dailyPickupCount
                0.8627787f,  // gameRatio (86%)
                0.10634648f,  // socialCommRatio
                0.02058319f,  // learningRatio
                0.0f  // entertainmentRatio
        );

        // when
        UserClassification result = service.classify(metrics);

        // then
        assertThat(result.getContentPreference())
                .isEqualTo(UserClassification.ContentPreference.GAME_FOCUSED);
        assertThat(result.getContentPreferenceName()).isEqualTo("게임 집중형");

        assertThat(result.getBehaviorPattern())
                .isEqualTo(UserClassification.BehaviorPattern.COMPULSIVE_CHECKER);
        assertThat(result.getBehaviorPatternName()).isEqualTo("강박적 확인형");

        // 계산 검증
        assertThat(result.getUsageSummary().getTotalScreenTimeHours())
                .isCloseTo(9.72, org.assertj.core.data.Offset.offset(0.01));  // 583분 = 9.72시간
        assertThat(result.getUsageSummary().getDailyPickupCount()).isEqualTo(249);
        assertThat(result.getUsageSummary().getAvgSessionDurationSeconds())
                .isCloseTo(140.4, org.assertj.core.data.Offset.offset(1.0));  // 583*60 / 249 ≈ 140초
    }

    @Test
    @DisplayName("소셜 집중형 사용자 분류")
    void classifySocialFocusedUser() {
        // given: 소셜 비율 70%, 총 480분(8시간)
        DailyMetricsSaved metrics = createMetrics(
                480,  // 8시간
                50,   // 적은 픽업 횟수
                0.05f,  // gameRatio
                0.70f,  // socialCommRatio (70%)
                0.10f,  // learningRatio
                0.15f   // entertainmentRatio
        );

        // when
        UserClassification result = service.classify(metrics);

        // then
        assertThat(result.getContentPreference())
                .isEqualTo(UserClassification.ContentPreference.SOCIAL_FOCUSED);
        assertThat(result.getBehaviorPattern())
                .isEqualTo(UserClassification.BehaviorPattern.NORMAL_BEHAVIOR);
    }

    @Test
    @DisplayName("학습 집중형 사용자 분류")
    void classifyLearningFocusedUser() {
        // given: 학습 비율 40%, 총 300분(5시간)
        DailyMetricsSaved metrics = createMetrics(
                300,  // 5시간
                80,
                0.10f,  // gameRatio
                0.15f,  // socialCommRatio
                0.40f,  // learningRatio (40%)
                0.35f   // entertainmentRatio
        );

        // when
        UserClassification result = service.classify(metrics);

        // then
        assertThat(result.getContentPreference())
                .isEqualTo(UserClassification.ContentPreference.LEARNING_FOCUSED);
        assertThat(result.getBehaviorPattern())
                .isEqualTo(UserClassification.BehaviorPattern.NORMAL_BEHAVIOR);
    }

    @Test
    @DisplayName("엔터테인먼트 집중형 사용자 분류")
    void classifyEntertainmentFocusedUser() {
        // given: 엔터 비율 50%, 총 360분(6시간)
        DailyMetricsSaved metrics = createMetrics(
                360,  // 6시간
                100,
                0.10f,  // gameRatio
                0.20f,  // socialCommRatio
                0.20f,  // learningRatio
                0.50f   // entertainmentRatio (50%)
        );

        // when
        UserClassification result = service.classify(metrics);

        // then
        assertThat(result.getContentPreference())
                .isEqualTo(UserClassification.ContentPreference.ENTERTAINMENT_FOCUSED);
    }

    @Test
    @DisplayName("균형잡힌 사용자 분류 - 특정 카테고리 지배적이지 않음")
    void classifyBalancedUser() {
        // given: 모든 카테고리 균등 분배
        DailyMetricsSaved metrics = createMetrics(
                240,  // 4시간
                60,
                0.25f,  // gameRatio
                0.25f,  // socialCommRatio
                0.25f,  // learningRatio
                0.25f   // entertainmentRatio
        );

        // when
        UserClassification result = service.classify(metrics);

        // then
        assertThat(result.getContentPreference())
                .isEqualTo(UserClassification.ContentPreference.BALANCED);
    }

    @Test
    @DisplayName("강박적 확인형 - 높은 픽업 + 짧은 세션")
    void classifyCompulsiveChecker() {
        // given: 150회 이상 픽업 + 평균 세션 30초 미만
        DailyMetricsSaved metrics = createMetrics(
                120,  // 2시간 = 120분 = 7200초
                300,  // 300회 픽업 -> 평균 세션 24초
                0.30f,  // gameRatio
                0.30f,  // socialCommRatio
                0.20f,  // learningRatio
                0.20f   // entertainmentRatio
        );

        // when
        UserClassification result = service.classify(metrics);

        // then
        assertThat(result.getBehaviorPattern())
                .isEqualTo(UserClassification.BehaviorPattern.COMPULSIVE_CHECKER);

        // 평균 세션 시간 확인: 7200초 / 300회 = 24초
        assertThat(result.getUsageSummary().getAvgSessionDurationSeconds())
                .isLessThan(30.0);
    }

    @Test
    @DisplayName("일반 행동 - 높은 픽업이지만 긴 세션 (몰입형)")
    void classifyNormalBehaviorWithHighPickup() {
        // given: 150회 이상 픽업하지만 평균 세션 30초 이상
        DailyMetricsSaved metrics = createMetrics(
                480,  // 8시간 = 480분 = 28800초
                200,  // 200회 픽업 -> 평균 세션 144초
                0.30f,  // gameRatio
                0.30f,  // socialCommRatio
                0.20f,  // learningRatio
                0.20f   // entertainmentRatio
        );

        // when
        UserClassification result = service.classify(metrics);

        // then
        assertThat(result.getBehaviorPattern())
                .isEqualTo(UserClassification.BehaviorPattern.NORMAL_BEHAVIOR);

        // 평균 세션 시간 확인: 28800초 / 200회 = 144초 (몰입형 사용)
        assertThat(result.getUsageSummary().getAvgSessionDurationSeconds())
                .isGreaterThanOrEqualTo(30.0);
    }

    @Test
    @DisplayName("임계값 경계 테스트 - 게임 비율만 높고 시간 부족")
    void classifyGameRatioHighButTimeInsufficient() {
        // given: 게임 비율 40% (임계값 30% 초과) 하지만 절대 시간 부족 (2시간 미만)
        DailyMetricsSaved metrics = createMetrics(
                180,  // 3시간 = 180분
                50,
                0.40f,  // gameRatio (40% > 30%)
                0.30f,  // socialCommRatio
                0.15f,  // learningRatio
                0.15f   // entertainmentRatio
        );
        // 게임 시간 = 180 * 60 * 0.4 = 4320초 = 1.2시간 < 2시간 (임계값)

        // when
        UserClassification result = service.classify(metrics);

        // then: 비율은 높지만 절대 시간이 부족하여 균형잡힌 사용으로 분류
        assertThat(result.getContentPreference())
                .isEqualTo(UserClassification.ContentPreference.BALANCED);
    }

    // 헬퍼 메서드
    private DailyMetricsSaved createMetrics(
            int totalUsageMinutes,
            int dailyPickupCount,
            float gameRatio,
            float socialCommRatio,
            float learningRatio,
            float entertainmentRatio
    ) {
        DailyMetricsSaved metrics = new DailyMetricsSaved();
        metrics.setEventType("DailyMetricsSaved");
        metrics.setTimestamp(System.currentTimeMillis());
        metrics.setSubjectId(UUID.randomUUID());
        metrics.setSummaryDate(LocalDate.now());
        metrics.setTotalUsageMinutes(totalUsageMinutes);
        metrics.setDailyPickupCount(dailyPickupCount);
        metrics.setGameRatio(gameRatio);
        metrics.setSocialCommRatio(socialCommRatio);
        metrics.setLearningRatio(learningRatio);
        metrics.setEntertainmentRatio(entertainmentRatio);

        // 기타 필드 (테스트에서 사용하지 않음)
        metrics.setMorningMinutes(0);
        metrics.setAfternoonMinutes(0);
        metrics.setEveningMinutes(0);
        metrics.setMostActiveHourStart(0);

        return metrics;
    }
}
