package screentimeai.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 일일 인사이트 요약
 */
@Entity
@Table(name = "insight_daily_summary")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(InsightDailySummaryId.class)
public class InsightDailySummaryEntity {

    @Id
    @Column(name = "subject_id")
    private UUID subjectId;

    @Id
    @Column(name = "summary_date")
    private LocalDate summaryDate;

    @Column(name = "total_usage_minutes")
    private Integer totalUsageMinutes;

    @Column(name = "daily_pickup_count")
    private Integer dailyPickupCount;

    @Column(name = "morning_minutes")
    private Integer morningMinutes;

    @Column(name = "afternoon_minutes")
    private Integer afternoonMinutes;

    @Column(name = "evening_minutes")
    private Integer eveningMinutes;

    @Column(name = "most_active_hour_start")
    private Integer mostActiveHourStart;
}
