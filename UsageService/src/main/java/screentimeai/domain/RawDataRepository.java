package screentimeai.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface RawDataRepository extends JpaRepository<RawData, RawDataId> {

    boolean existsBySubjectIdAndTimestamp(UUID subjectId, Instant timestamp);
}
