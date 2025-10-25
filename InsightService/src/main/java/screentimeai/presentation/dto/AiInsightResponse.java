package screentimeai.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 인사이트 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "AI 기반 인사이트 요약")
public class AiInsightResponse {

    @Schema(
        description = "AI가 생성한 인사이트 요약 문장 (~요 체 사용, 요일별 패턴 중심)",
        example = "목요일에 사용량이 최대치를 기록했어요.",
        examples = {
            "목요일에 사용량이 최대치를 기록했어요.",
            "주말보다 평일에 스마트폰 사용이 더 많아요.",
            "월요일에 픽업 횟수가 가장 높았어요.",
            "화요일과 수요일에 사용량이 급증했어요.",
            "분석할 데이터가 충분하지 않습니다."
        }
    )
    private String summary;
}
