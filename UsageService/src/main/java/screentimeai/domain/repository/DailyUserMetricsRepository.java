package screentimeai.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import screentimeai.domain.entity.DailyUserMetrics;
import screentimeai.domain.entity.DailyUserMetricsId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DailyUserMetricsRepository extends JpaRepository<DailyUserMetrics, DailyUserMetricsId> {

    Optional<DailyUserMetrics> findBySubjectIdAndSummaryDate(UUID subjectId, LocalDate summaryDate);

    List<DailyUserMetrics> findBySubjectIdAndSummaryDateBetweenOrderBySummaryDateDesc(
            UUID subjectId, LocalDate startDate, LocalDate endDate);
}
