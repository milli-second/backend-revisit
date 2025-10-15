package screentimeai.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import screentimeai.domain.entity.DailyTopApps;
import screentimeai.domain.entity.DailyTopAppsId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface DailyTopAppsRepository extends JpaRepository<DailyTopApps, DailyTopAppsId> {

    List<DailyTopApps> findBySubjectIdAndSummaryDate(UUID subjectId, LocalDate summaryDate);

    @Transactional
    @Modifying
    void deleteBySubjectIdAndSummaryDate(UUID subjectId, LocalDate summaryDate);
}
