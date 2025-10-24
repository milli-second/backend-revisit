package screentimeai.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 콘텐츠 선호 분류 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "콘텐츠 선호 분류")
public class ContentPreferenceResponse {

    /**
     * 날짜
     */
    @Schema(description = "날짜", example = "2025-10-23")
    private LocalDate date;

    /**
     * 콘텐츠 선호 분류 (영문)
     */
    @Schema(description = "콘텐츠 선호 분류", example = "GAME_FOCUSED",
            allowableValues = {"GAME_FOCUSED", "SOCIAL_FOCUSED", "LEARNING_FOCUSED", "ENTERTAINMENT_FOCUSED", "BALANCED"})
    private String contentPreference;

    /**
     * 콘텐츠 선호 분류 (한글)
     */
    @Schema(description = "콘텐츠 선호 분류 (한글)", example = "게임 집중형")
    private String contentPreferenceKo;
}
