package screentimeai.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 사용자 분류 결과 저장 (날짜별)
 */
@Entity
@Table(name = "user_classification")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(UserClassificationId.class)
public class UserClassificationEntity {

    @Id
    @Column(name = "subject_id")
    private UUID subjectId;

    @Id
    @Column(name = "classified_date")
    private LocalDate classifiedDate;

    @Column(name = "classification_type", nullable = false)
    private String classificationType;  // 예: "GAME_FOCUSED|COMPULSIVE_CHECKER"

    @Column(name = "behavior_pattern")
    private String behaviorPattern;  // COMPULSIVE_CHECKER, NORMAL_BEHAVIOR

    @Column(name = "content_preference")
    private String contentPreference;  // SOCIAL_FOCUSED, GAME_FOCUSED, etc.
}
