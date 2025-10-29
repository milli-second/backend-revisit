package screentimeai.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 행동 패턴 분류 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "행동 패턴 분류")
public class BehaviorPatternResponse {

    /**
     * 날짜
     */
    @Schema(description = "날짜", example = "2025-10-23")
    private LocalDate date;

    /**
     * 행동 패턴 분류 (영문)
     */
    @Schema(description = "행동 패턴 분류", example = "COMPULSIVE_CHECKER",
            allowableValues = {"COMPULSIVE_CHECKER", "NORMAL_BEHAVIOR"})
    private String behaviorPattern;

    /**
     * 행동 패턴 분류 (한글)
     */
    @Schema(description = "행동 패턴 분류 (한글)", example = "강박적 확인형")
    private String behaviorPatternKo;
}
