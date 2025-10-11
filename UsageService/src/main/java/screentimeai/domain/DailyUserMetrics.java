package screentimeai.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "daily_user_metrics")
@IdClass(DailyUserMetricsId.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyUserMetrics {

    @Id
    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Id
    @Column(name = "summary_date", nullable = false)
    private LocalDate summaryDate;

    @Column(name = "total_usage_minutes")
    private Integer totalUsageMinutes;

    @Column(name = "dawn_minutes")
    private Integer dawnMinutes;

    @Column(name = "morning_minutes")
    private Integer morningMinutes;

    @Column(name = "afternoon_minutes")
    private Integer afternoonMinutes;

    @Column(name = "evening_minutes")
    private Integer eveningMinutes;

    @Column(name = "dawn_sns_minutes")
    private Integer dawnSnsMinutes;

    @Column(name = "most_active_hour_start")
    private Integer mostActiveHourStart;

    @Column(name = "daily_pickup_count")
    private Integer dailyPickupCount;

    @Column(name = "game_ratio")
    private Float gameRatio;

    @Column(name = "social_comm_ratio")
    private Float socialCommRatio;

    @Column(name = "learning_ratio")
    private Float learningRatio;

    @Column(name = "entertainment_ratio")
    private Float entertainmentRatio;
}
