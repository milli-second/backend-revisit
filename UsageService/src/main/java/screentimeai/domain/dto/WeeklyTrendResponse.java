package screentimeai.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * 주간 변화 트렌드 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "주간 변화 트렌드")
public class WeeklyTrendResponse {

    /**
     * 기준 날짜 (어제)
     */
    @Schema(description = "기준 날짜 (어제)", example = "2025-10-23")
    private LocalDate baseDate;

    /**
     * 총 사용시간 변화
     */
    @Schema(description = "총 사용시간 변화")
    private TotalUsageChange totalUsageChange;

    /**
     * 카테고리별 변화 TOP 3
     */
    @Schema(description = "변화율이 큰 카테고리 TOP 3")
    private List<CategoryTrend> top3Categories;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "총 사용시간 변화")
    public static class TotalUsageChange {
        /**
         * 어제 사용시간 (분)
         */
        @Schema(description = "어제 총 사용시간 (분)", example = "420")
        private Integer yesterdayMinutes;

        /**
         * 최근 7일 평균 (분)
         */
        @Schema(description = "최근 7일 평균 사용시간 (분)", example = "350")
        private Double weeklyAverageMinutes;

        /**
         * 변화량 (분)
         */
        @Schema(description = "변화량 (분)", example = "70")
        private Double changeMinutes;

        /**
         * 변화율 (%)
         */
        @Schema(description = "변화율 (%)", example = "20.0")
        private Double changeRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "카테고리별 변화 트렌드")
    public static class CategoryTrend {
        /**
         * 카테고리 ID
         */
        @Schema(description = "Google Play 카테고리 ID", example = "1")
        private Integer categoryId;

        /**
         * 카테고리 이름
         */
        @Schema(description = "카테고리 이름", example = "GAME_ACTION")
        private String categoryName;

        /**
         * 어제 사용시간 (분)
         */
        @Schema(description = "어제 사용시간 (분)", example = "150")
        private Integer yesterdayMinutes;

        /**
         * 최근 7일 평균 (분)
         */
        @Schema(description = "최근 7일 평균 사용시간 (분)", example = "60")
        private Double weeklyAverageMinutes;

        /**
         * 변화량 (분)
         */
        @Schema(description = "변화량 (분)", example = "90")
        private Double changeMinutes;

        /**
         * 변화율 (%)
         */
        @Schema(description = "변화율 (%)", example = "150.0")
        private Double changeRate;
    }
}
