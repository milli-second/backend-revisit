package screentimeai.domain.entity;

import lombok.Data;
import lombok.ToString;
import screentimeai.infra.AbstractEvent;

import java.time.LocalDate;
import java.util.UUID;

@Data
@ToString
public class DailyMetricsSaved extends AbstractEvent {
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

    public DailyMetricsSaved(DailyUserMetrics metrics) {
        super();
        this.subjectId = metrics.getSubjectId();
        this.summaryDate = metrics.getSummaryDate();
        this.totalUsageMinutes = metrics.getTotalUsageMinutes();
        this.morningMinutes = metrics.getMorningMinutes();
        this.afternoonMinutes = metrics.getAfternoonMinutes();
        this.eveningMinutes = metrics.getEveningMinutes();
        this.mostActiveHourStart = metrics.getMostActiveHourStart();
        this.dailyPickupCount = metrics.getDailyPickupCount();
        this.gameRatio = metrics.getGameRatio();
        this.socialCommRatio = metrics.getSocialCommRatio();
        this.learningRatio = metrics.getLearningRatio();
        this.entertainmentRatio = metrics.getEntertainmentRatio();
    }

    public DailyMetricsSaved() {
        super();
    }
}
