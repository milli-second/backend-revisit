package screentimeai.infra.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import screentimeai.domain.entity.InsightDailyCategoryMetricsEntity;
import screentimeai.domain.entity.InsightDailyCategoryMetricsId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface InsightDailyCategoryMetricsRepository extends JpaRepository<InsightDailyCategoryMetricsEntity, InsightDailyCategoryMetricsId> {

    /**
     * 특정 사용자의 특정 기간 카테고리별 메트릭 조회
     */
    @Query("SELECT e FROM InsightDailyCategoryMetricsEntity e WHERE e.subjectId = :subjectId AND e.summaryDate >= :startDate AND e.summaryDate <= :endDate")
    List<InsightDailyCategoryMetricsEntity> findBySubjectIdAndDateRange(
            @Param("subjectId") UUID subjectId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}
