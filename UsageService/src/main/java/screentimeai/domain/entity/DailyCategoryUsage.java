package screentimeai.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "daily_category_usage")
@IdClass(DailyCategoryUsageId.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyCategoryUsage {

    @Id
    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Id
    @Column(name = "summary_date", nullable = false)
    private LocalDate summaryDate;

    @Id
    @Column(name = "category_id", nullable = false)
    private Integer categoryId;

    @Column(name = "usage_minutes")
    private Integer usageMinutes;
}
