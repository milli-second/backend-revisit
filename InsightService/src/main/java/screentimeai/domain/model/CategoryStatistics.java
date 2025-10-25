package screentimeai.domain.model;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * 카테고리 통계 (임계값 계산용)
 */
@Getter
@Builder
@ToString
public class CategoryStatistics {
    private String category;
    private double ratioThreshold;
    private int timeThresholdSeconds;

    // 분석 정보
    private double dominantAvg;
    private double percentile75;
    private double meanPlusHalfStd;
    private double actualAvg;
    private double dominantTimeHours;
    private double medianTimeHours;
}
