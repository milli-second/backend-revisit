package screentimeai.infra.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import screentimeai.domain.entity.InsightSummaryEntity;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InsightSummaryRepository extends JpaRepository<InsightSummaryEntity, UUID> {

    Optional<InsightSummaryEntity> findByUserIdAndUpdatedAtAfter(UUID userId, LocalDateTime updatedAt);

}
