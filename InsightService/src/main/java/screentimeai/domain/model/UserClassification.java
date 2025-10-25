package screentimeai.domain.model;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.List;
import java.util.Map;

/**
 * 사용자 분류 결과
 *
 * Python의 user_classifier.py의 classify_user() 반환값을 모델링
 * 다중 라벨 분류: 행동 패턴 + 콘텐츠 선호
 */
@Getter
@Builder
@ToString
public class UserClassification {

    /**
     * 행동 패턴
     */
    private BehaviorPattern behaviorPattern;
    private String behaviorPatternName;
    private List<String> behaviorReason;

    /**
     * 콘텐츠 선호
     */
    private ContentPreference contentPreference;
    private String contentPreferenceName;
    private List<String> contentReason;

    /**
     * 사용 요약 정보
     */
    private UsageSummary usageSummary;

    /**
     * 행동 패턴 유형
     */
    public enum BehaviorPattern {
        COMPULSIVE_CHECKER("강박적 확인형"),
        NORMAL_BEHAVIOR("일반 행동");

        private final String displayName;

        BehaviorPattern(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * 콘텐츠 선호 유형
     */
    public enum ContentPreference {
        SOCIAL_FOCUSED("소셜 집중형"),
        GAME_FOCUSED("게임 집중형"),
        LEARNING_FOCUSED("학습 집중형"),
        ENTERTAINMENT_FOCUSED("엔터테인먼트 집중형"),
        BALANCED("균형잡힌 사용");

        private final String displayName;

        ContentPreference(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * 사용 요약 정보
     */
    @Getter
    @Builder
    @ToString
    public static class UsageSummary {
        private double totalScreenTimeHours;
        private int dailyPickupCount;
        private double avgSessionDurationSeconds;
        private String dominantCategory;
        private double dominantRatio;
        private Map<String, Double> categoryTimeHours;  // 카테고리별 시간 (시간 단위)
    }
}
