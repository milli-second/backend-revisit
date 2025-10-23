package screentimeai.domain.entity;

import lombok.*;
import screentimeai.infra.AbstractEvent;

@Data
@ToString
public class DailyAnalysisDataPrepared extends AbstractEvent {

    private Long userId;
    private Object dailySummary;
}
