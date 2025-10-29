package screentimeai.domain.entity;

import lombok.*;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;

/**
 * UserSnsInsight 복합키
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class UserSnsInsightId implements Serializable {
    private UUID subjectId;
    private LocalDate calculatedDate;
}
