package screentimeai.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "daily_top_apps")
@IdClass(DailyTopAppsId.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyTopApps {

    @Id
    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Id
    @Column(name = "summary_date", nullable = false)
    private LocalDate summaryDate;

    @Id
    @Column(name = "app_rank", nullable = false)
    private Integer appRank;

    @Column(name = "package_name", nullable = false)
    private String packageName;

    @Column(name = "usage_minutes")
    private Integer usageMinutes;
}
