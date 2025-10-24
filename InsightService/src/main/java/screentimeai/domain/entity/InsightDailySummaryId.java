package screentimeai.domain.entity;

import lombok.*;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;

/**
 * InsightDailySummary 복합키
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class InsightDailySummaryId implements Serializable {
    private UUID subjectId;
    private LocalDate summaryDate;
}
