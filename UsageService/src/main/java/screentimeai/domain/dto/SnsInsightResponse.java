package screentimeai.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * SNS 인사이트 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SnsInsightResponse {
    /**
     * 전체 사용시간 중 SNS 비율 (%)
     */
    private Float snsUsageRate;

    /**
     * SNS 사용시간 중 새벽 접속 비율 (%)
     */
    private Float dawnAccessRate;

    /**
     * 계산된 날짜 (어제)
     */
    private LocalDate calculatedDate;
}
