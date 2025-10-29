package screentimeai.presentation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TodayUsageResponse {
    private LocalDate date;
    private Integer totalUsageMinutes;
    private Integer pickupCount;
}
