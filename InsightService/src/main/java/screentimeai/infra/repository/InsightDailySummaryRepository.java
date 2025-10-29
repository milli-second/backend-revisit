package screentimeai.infra.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import screentimeai.domain.entity.InsightDailySummaryEntity;
import screentimeai.domain.entity.InsightDailySummaryId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface InsightDailySummaryRepository extends JpaRepository<InsightDailySummaryEntity, InsightDailySummaryId> {

    /**
     * 특정 사용자의 최근 N일 데이터 조회
     */
    @Query("SELECT e FROM InsightDailySummaryEntity e WHERE e.subjectId = :subjectId AND e.summaryDate >= :startDate ORDER BY e.summaryDate DESC")
    List<InsightDailySummaryEntity> findRecentBySubjectId(
            @Param("subjectId") UUID subjectId,
            @Param("startDate") LocalDate startDate
    );
}
