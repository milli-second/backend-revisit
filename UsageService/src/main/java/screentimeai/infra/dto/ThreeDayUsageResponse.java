package screentimeai.infra.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ThreeDayUsageResponse {
    private List<DailyUsageSummary> dailySummaries;
    private Integer mostActiveHourStart;
}
