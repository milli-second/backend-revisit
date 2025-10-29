package screentimeai.domain.config;

import lombok.Getter;
import org.springframework.stereotype.Component;

/**
 * 사용자 분류를 위한 임계값 설정
 *
 * Python 프로젝트의 improved_threshold_calculator.py에서 계산된 값을 기반으로
 * 고정된 임계값을 사용합니다.
 *
 * 임계값은 다음과 같은 방법으로 계산되었습니다:
 * - 비율 임계값: 지배적 사용자 평균(40%) + 75th percentile(30%) + Mean+0.5*Std(20%) + 실제 사용자 평균(10%)
 * - 시간 임계값: 지배적 사용자 평균(50%) + 중앙값(50%)
 * - 픽업 임계값: 80th percentile
 */
@Component
@Getter
public class ClassificationThresholds {

    /**
     * 소셜/커뮤니케이션 집중형 임계값
     */
    public static class SocialFocused {
        public static final double RATIO_THRESHOLD = 0.20;  // 20% 이상 (기존 25%에서 완화)
        public static final int TIME_THRESHOLD_SECONDS = 3600;  // 1시간 이상
    }

    /**
     * 게임 집중형 임계값
     */
    public static class GameFocused {
        public static final double RATIO_THRESHOLD = 0.25;  // 25% 이상 (기존 30%에서 완화)
        public static final int TIME_THRESHOLD_SECONDS = 7200;  // 2시간 이상 (소수 클래스 보호)
    }

    /**
     * 학습 집중형 임계값
     */
    public static class LearningFocused {
        public static final double RATIO_THRESHOLD = 0.15;  // 15% 이상 (기존 20%에서 완화)
        public static final int TIME_THRESHOLD_SECONDS = 1800;  // 30분 이상
    }

    /**
     * 엔터테인먼트 집중형 임계값
     */
    public static class EntertainmentFocused {
        public static final double RATIO_THRESHOLD = 0.20;  // 20% 이상 (기존 25%에서 완화)
        public static final int TIME_THRESHOLD_SECONDS = 3600;  // 1시간 이상
    }

    /**
     * 강박적 확인형 임계값
     */
    public static class CompulsiveChecker {
        public static final int PICKUP_THRESHOLD = 150;  // 150회 이상 (80th percentile 기준)
        public static final double AVG_SESSION_THRESHOLD_SECONDS = 30.0;  // 평균 세션 30초 미만
    }

    /**
     * 카테고리별 임계값을 조회하는 헬퍼 메서드
     */
    public ThresholdInfo getThresholdForCategory(String category) {
        return switch (category.toLowerCase()) {
            case "social_comm", "social" -> new ThresholdInfo(
                    SocialFocused.RATIO_THRESHOLD,
                    SocialFocused.TIME_THRESHOLD_SECONDS
            );
            case "game" -> new ThresholdInfo(
                    GameFocused.RATIO_THRESHOLD,
                    GameFocused.TIME_THRESHOLD_SECONDS
            );
            case "learning" -> new ThresholdInfo(
                    LearningFocused.RATIO_THRESHOLD,
                    LearningFocused.TIME_THRESHOLD_SECONDS
            );
            case "entertainment" -> new ThresholdInfo(
                    EntertainmentFocused.RATIO_THRESHOLD,
                    EntertainmentFocused.TIME_THRESHOLD_SECONDS
            );
            default -> throw new IllegalArgumentException("Unknown category: " + category);
        };
    }

    @Getter
    public static class ThresholdInfo {
        private final double ratioThreshold;
        private final int timeThresholdSeconds;

        public ThresholdInfo(double ratioThreshold, int timeThresholdSeconds) {
            this.ratioThreshold = ratioThreshold;
            this.timeThresholdSeconds = timeThresholdSeconds;
        }
    }
}
