package screentimeai.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import screentimeai.domain.config.ClassificationThresholds;
import screentimeai.domain.entity.DailyMetricsSaved;
import screentimeai.domain.model.UserClassification;

import java.util.*;

/**
 * 사용자 분류 서비스
 *
 * Python의 user_classifier.py의 classify_user() 로직을 Java로 구현
 *
 * 분류 방식:
 * 1. 행동 패턴: 픽업 횟수 + 평균 세션 시간 기반
 * 2. 콘텐츠 선호: 카테고리별 비율 + 절대 시간 기반
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserClassificationService {

    private final ClassificationThresholds thresholds;

    /**
     * Kafka 이벤트(DailyMetricsSaved)로부터 사용자 분류 수행
     *
     * @param metrics Kafka로부터 수신한 일일 메트릭
     * @return 분류 결과
     */
    public UserClassification classify(DailyMetricsSaved metrics) {
        log.debug("Classifying user: subjectId={}", metrics.getSubjectId());

        // 1. 데이터 추출 및 변환 (분 -> 초)
        int totalScreenTimeSeconds = metrics.getTotalUsageMinutes() * 60;
        int dailyPickupCount = metrics.getDailyPickupCount();

        float gameRatio = metrics.getGameRatio();
        float socialCommRatio = metrics.getSocialCommRatio();
        float learningRatio = metrics.getLearningRatio();
        float entertainmentRatio = metrics.getEntertainmentRatio();

        // 2. 카테고리별 절대 시간 계산 (초 단위)
        int gameTimeSeconds = (int) (totalScreenTimeSeconds * gameRatio);
        int socialCommTimeSeconds = (int) (totalScreenTimeSeconds * socialCommRatio);
        int learningTimeSeconds = (int) (totalScreenTimeSeconds * learningRatio);
        int entertainmentTimeSeconds = (int) (totalScreenTimeSeconds * entertainmentRatio);

        // 3. 지배적 카테고리 찾기
        Map<String, Float> categoryRatios = Map.of(
                "game", gameRatio,
                "social_comm", socialCommRatio,
                "learning", learningRatio,
                "entertainment", entertainmentRatio
        );

        String dominantCategory = categoryRatios.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("game");

        float dominantRatio = categoryRatios.get(dominantCategory);

        // 4. 평균 세션 시간 계산
        double avgSessionDurationSeconds = 0.0;
        if (dailyPickupCount > 0) {
            avgSessionDurationSeconds = (double) totalScreenTimeSeconds / dailyPickupCount;
        }

        // 5. 행동 패턴 분류
        ClassificationResult behaviorResult = classifyBehaviorPattern(
                dailyPickupCount,
                avgSessionDurationSeconds
        );

        // 6. 콘텐츠 선호 분류
        ClassificationResult contentResult = classifyContentPreference(
                gameRatio, gameTimeSeconds,
                socialCommRatio, socialCommTimeSeconds,
                learningRatio, learningTimeSeconds,
                entertainmentRatio, entertainmentTimeSeconds,
                dominantCategory, dominantRatio
        );

        // 7. 사용 요약 정보 구성
        UserClassification.UsageSummary usageSummary = UserClassification.UsageSummary.builder()
                .totalScreenTimeHours(totalScreenTimeSeconds / 3600.0)
                .dailyPickupCount(dailyPickupCount)
                .avgSessionDurationSeconds(avgSessionDurationSeconds)
                .dominantCategory(dominantCategory)
                .dominantRatio(dominantRatio)
                .categoryTimeHours(Map.of(
                        "game", gameTimeSeconds / 3600.0,
                        "social_comm", socialCommTimeSeconds / 3600.0,
                        "learning", learningTimeSeconds / 3600.0,
                        "entertainment", entertainmentTimeSeconds / 3600.0
                ))
                .build();

        // 8. 최종 결과 반환
        UserClassification classification = UserClassification.builder()
                .behaviorPattern(behaviorResult.behaviorPattern)
                .behaviorPatternName(behaviorResult.behaviorPattern.getDisplayName())
                .behaviorReason(behaviorResult.reasons)
                .contentPreference(contentResult.contentPreference)
                .contentPreferenceName(contentResult.contentPreference.getDisplayName())
                .contentReason(contentResult.reasons)
                .usageSummary(usageSummary)
                .build();

        log.info("Classification result: subjectId={}, behavior={}, content={}",
                metrics.getSubjectId(),
                classification.getBehaviorPattern(),
                classification.getContentPreference());

        return classification;
    }

    /**
     * 행동 패턴 분류
     */
    private ClassificationResult classifyBehaviorPattern(
            int dailyPickupCount,
            double avgSessionDurationSeconds
    ) {
        List<String> reasons = new ArrayList<>();

        // 강박적 확인형: 높은 픽업 횟수 + 짧은 세션 시간
        if (dailyPickupCount >= ClassificationThresholds.CompulsiveChecker.PICKUP_THRESHOLD &&
            avgSessionDurationSeconds < ClassificationThresholds.CompulsiveChecker.AVG_SESSION_THRESHOLD_SECONDS) {

            reasons.add(String.format("픽업 %d회 >= %d회",
                    dailyPickupCount,
                    ClassificationThresholds.CompulsiveChecker.PICKUP_THRESHOLD));
            reasons.add(String.format("평균 세션 %.1f초 < 30초 (강박적 확인 패턴)",
                    avgSessionDurationSeconds));

            return new ClassificationResult(
                    UserClassification.BehaviorPattern.COMPULSIVE_CHECKER,
                    null,
                    reasons
            );
        }

        // 일반 행동
        if (dailyPickupCount >= ClassificationThresholds.CompulsiveChecker.PICKUP_THRESHOLD) {
            reasons.add(String.format("픽업 %d회는 높지만, 평균 세션 %.1f초 (몰입형 사용)",
                    dailyPickupCount, avgSessionDurationSeconds));
        } else {
            reasons.add(String.format("픽업 %d회 < %d회",
                    dailyPickupCount,
                    ClassificationThresholds.CompulsiveChecker.PICKUP_THRESHOLD));
            reasons.add(String.format("평균 세션 %.1f초 (정상 범위)", avgSessionDurationSeconds));
        }

        return new ClassificationResult(
                UserClassification.BehaviorPattern.NORMAL_BEHAVIOR,
                null,
                reasons
        );
    }

    /**
     * 콘텐츠 선호 분류
     */
    private ClassificationResult classifyContentPreference(
            float gameRatio, int gameTimeSeconds,
            float socialCommRatio, int socialCommTimeSeconds,
            float learningRatio, int learningTimeSeconds,
            float entertainmentRatio, int entertainmentTimeSeconds,
            String dominantCategory, float dominantRatio
    ) {
        List<String> reasons = new ArrayList<>();

        // 소셜/커뮤니케이션 집중형
        if (socialCommRatio > ClassificationThresholds.SocialFocused.RATIO_THRESHOLD &&
            socialCommTimeSeconds >= ClassificationThresholds.SocialFocused.TIME_THRESHOLD_SECONDS) {

            reasons.add(String.format("소셜/커뮤니케이션 비율 %.1f%% > %.1f%%",
                    socialCommRatio * 100,
                    ClassificationThresholds.SocialFocused.RATIO_THRESHOLD * 100));
            reasons.add(String.format("소셜/커뮤니케이션 시간 %.2fh >= %.2fh",
                    socialCommTimeSeconds / 3600.0,
                    ClassificationThresholds.SocialFocused.TIME_THRESHOLD_SECONDS / 3600.0));

            return new ClassificationResult(
                    null,
                    UserClassification.ContentPreference.SOCIAL_FOCUSED,
                    reasons
            );
        }

        // 게임 집중형
        if (gameRatio > ClassificationThresholds.GameFocused.RATIO_THRESHOLD &&
            gameTimeSeconds >= ClassificationThresholds.GameFocused.TIME_THRESHOLD_SECONDS) {

            reasons.add(String.format("게임 비율 %.1f%% > %.1f%%",
                    gameRatio * 100,
                    ClassificationThresholds.GameFocused.RATIO_THRESHOLD * 100));
            reasons.add(String.format("게임 시간 %.2fh >= %.2fh",
                    gameTimeSeconds / 3600.0,
                    ClassificationThresholds.GameFocused.TIME_THRESHOLD_SECONDS / 3600.0));

            return new ClassificationResult(
                    null,
                    UserClassification.ContentPreference.GAME_FOCUSED,
                    reasons
            );
        }

        // 학습 집중형
        if (learningRatio > ClassificationThresholds.LearningFocused.RATIO_THRESHOLD &&
            learningTimeSeconds >= ClassificationThresholds.LearningFocused.TIME_THRESHOLD_SECONDS) {

            reasons.add(String.format("학습 비율 %.1f%% > %.1f%%",
                    learningRatio * 100,
                    ClassificationThresholds.LearningFocused.RATIO_THRESHOLD * 100));
            reasons.add(String.format("학습 시간 %.2fh >= %.2fh",
                    learningTimeSeconds / 3600.0,
                    ClassificationThresholds.LearningFocused.TIME_THRESHOLD_SECONDS / 3600.0));

            return new ClassificationResult(
                    null,
                    UserClassification.ContentPreference.LEARNING_FOCUSED,
                    reasons
            );
        }

        // 엔터테인먼트 집중형
        if (entertainmentRatio > ClassificationThresholds.EntertainmentFocused.RATIO_THRESHOLD &&
            entertainmentTimeSeconds >= ClassificationThresholds.EntertainmentFocused.TIME_THRESHOLD_SECONDS) {

            reasons.add(String.format("엔터테인먼트 비율 %.1f%% > %.1f%%",
                    entertainmentRatio * 100,
                    ClassificationThresholds.EntertainmentFocused.RATIO_THRESHOLD * 100));
            reasons.add(String.format("엔터 시간 %.2fh >= %.2fh",
                    entertainmentTimeSeconds / 3600.0,
                    ClassificationThresholds.EntertainmentFocused.TIME_THRESHOLD_SECONDS / 3600.0));

            return new ClassificationResult(
                    null,
                    UserClassification.ContentPreference.ENTERTAINMENT_FOCUSED,
                    reasons
            );
        }

        // 균형잡힌 사용
        reasons.add(String.format("특정 카테고리 지배적이지 않음 (가장 높은 비율: %s %.1f%%)",
                dominantCategory, dominantRatio * 100));

        return new ClassificationResult(
                null,
                UserClassification.ContentPreference.BALANCED,
                reasons
        );
    }

    /**
     * 분류 결과를 담는 내부 헬퍼 클래스
     */
    private static class ClassificationResult {
        UserClassification.BehaviorPattern behaviorPattern;
        UserClassification.ContentPreference contentPreference;
        List<String> reasons;

        ClassificationResult(
                UserClassification.BehaviorPattern behaviorPattern,
                UserClassification.ContentPreference contentPreference,
                List<String> reasons
        ) {
            this.behaviorPattern = behaviorPattern;
            this.contentPreference = contentPreference;
            this.reasons = reasons;
        }
    }
}
