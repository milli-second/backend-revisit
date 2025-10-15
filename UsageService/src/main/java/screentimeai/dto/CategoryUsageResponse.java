package screentimeai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "카테고리별 사용량 응답")
public class CategoryUsageResponse {

    @Schema(description = "카테고리 이름", example = "GAME")
    private String categoryName;

    @Schema(description = "사용 비율 (0.0 ~ 1.0)", example = "0.35")
    private Float ratio;
}
