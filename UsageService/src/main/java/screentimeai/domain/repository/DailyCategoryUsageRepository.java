package screentimeai.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import screentimeai.domain.entity.DailyCategoryUsage;
import screentimeai.domain.entity.DailyCategoryUsageId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface DailyCategoryUsageRepository extends JpaRepository<DailyCategoryUsage, DailyCategoryUsageId> {
    List<DailyCategoryUsage> findBySubjectIdAndSummaryDate(UUID subjectId, LocalDate summaryDate);
}
