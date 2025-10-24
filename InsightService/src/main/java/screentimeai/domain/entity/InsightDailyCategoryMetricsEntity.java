package screentimeai.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 일일 카테고리별 메트릭
 */
@Entity
@Table(name = "insight_daily_category_metrics")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(InsightDailyCategoryMetricsId.class)
public class InsightDailyCategoryMetricsEntity {

    @Id
    @Column(name = "subject_id")
    private UUID subjectId;

    @Id
    @Column(name = "summary_date")
    private LocalDate summaryDate;

    @Id
    @Column(name = "category_id")
    private Integer categoryId;

    @Column(name = "usage_minutes")
    private Integer usageMinutes;
}
