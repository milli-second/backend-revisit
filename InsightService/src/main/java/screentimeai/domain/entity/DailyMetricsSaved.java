package screentimeai.domain.entity;

import lombok.Data;
import lombok.ToString;

import java.time.LocalDate;
import java.util.UUID;

@Data
@ToString
public class DailyMetricsSaved {
    private String eventType;
    private Long timestamp;

    private UUID subjectId;
    private LocalDate summaryDate;
    private Integer totalUsageMinutes;
    private Integer morningMinutes;
    private Integer afternoonMinutes;
    private Integer eveningMinutes;
    private Integer mostActiveHourStart;
    private Integer dailyPickupCount;
    private Float gameRatio;
    private Float socialCommRatio;
    private Float learningRatio;
    private Float entertainmentRatio;
}
