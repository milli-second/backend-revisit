package screentimeai.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DailyUserMetricsRepository extends JpaRepository<DailyUserMetrics, DailyUserMetricsId> {

    Optional<DailyUserMetrics> findBySubjectIdAndSummaryDate(UUID subjectId, LocalDate summaryDate);

    boolean existsBySubjectIdAndSummaryDate(UUID subjectId, LocalDate summaryDate);
}
