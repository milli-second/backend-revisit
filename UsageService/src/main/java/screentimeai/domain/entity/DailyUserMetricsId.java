package screentimeai.domain.entity;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyUserMetricsId implements Serializable {
    private UUID subjectId;
    private LocalDate summaryDate;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DailyUserMetricsId that = (DailyUserMetricsId) o;
        return Objects.equals(subjectId, that.subjectId) &&
               Objects.equals(summaryDate, that.summaryDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subjectId, summaryDate);
    }
}
