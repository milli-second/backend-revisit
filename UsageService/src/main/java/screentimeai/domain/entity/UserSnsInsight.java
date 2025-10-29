package screentimeai.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 사용자 SNS 인사이트
 *
 * SNS 사용 패턴 분석 결과 저장
 * - 전체 중 SNS 비율
 * - SNS 중 새벽 접속 비율
 */
@Entity
@Table(name = "user_sns_insight")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(UserSnsInsightId.class)
public class UserSnsInsight {

    @Id
    @Column(name = "subject_id")
    private UUID subjectId;

    @Id
    @Column(name = "calculated_date")
    private LocalDate calculatedDate;

    @Column(name = "sns_usage_rate")
    private Float snsUsageRate;  // 전체 중 SNS 비율 (%)

    @Column(name = "dawn_access_rate")
    private Float dawnAccessRate;  // SNS 중 새벽 비율 (%)

    @Column(name = "total_sns_minutes")
    private Integer totalSnsMinutes;  // SNS 총 사용시간 (분)
}
