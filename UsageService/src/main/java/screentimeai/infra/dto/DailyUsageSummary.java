package screentimeai.infra.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyUsageSummary {
    private LocalDate date;
    private Integer dawnMinutes;
    private Integer morningMinutes;
    private Integer afternoonMinutes;
    private Integer eveningMinutes;
}
