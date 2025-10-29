package screentimeai.infra.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import screentimeai.domain.entity.UserSnsInsight;
import screentimeai.domain.entity.UserSnsInsightId;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserSnsInsightRepository extends JpaRepository<UserSnsInsight, UserSnsInsightId> {

    /**
     * 특정 사용자의 특정 날짜 SNS 인사이트 조회
     */
    Optional<UserSnsInsight> findBySubjectIdAndCalculatedDate(UUID subjectId, LocalDate calculatedDate);

    /**
     * 특정 사용자의 가장 최근 SNS 인사이트 조회
     */
    @Query("SELECT u FROM UserSnsInsight u WHERE u.subjectId = :subjectId ORDER BY u.calculatedDate DESC LIMIT 1")
    Optional<UserSnsInsight> findLatestBySubjectId(@Param("subjectId") UUID subjectId);
}
