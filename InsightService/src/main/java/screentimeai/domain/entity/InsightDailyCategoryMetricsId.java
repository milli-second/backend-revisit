package screentimeai.domain.entity;

import lombok.*;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;

/**
 * InsightDailyCategoryMetrics 복합키
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class InsightDailyCategoryMetricsId implements Serializable {
    private UUID subjectId;
    private LocalDate summaryDate;
    private Integer categoryId;
}
