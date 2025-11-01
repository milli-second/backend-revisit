package screentimeai.infra.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import screentimeai.domain.entity.RawData;
import screentimeai.domain.entity.RawDataId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface RawDataRepository extends JpaRepository<RawData, RawDataId> {

    List<RawData> findBySubjectId(UUID subjectId);

    RawData findFirstBySubjectIdOrderByTimestampDesc(UUID subjectId);

    List<RawData> findBySubjectIdAndTimestampAfter(UUID subjectId, Instant cutoff);

    List<RawData> findBySubjectIdAndTimestampBetween(UUID subjectId, Instant startTime, Instant endTime);
}
