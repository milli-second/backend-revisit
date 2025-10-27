package screentimeai.infra.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import screentimeai.domain.entity.UserClassificationEntity;
import screentimeai.domain.entity.UserClassificationId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface UserClassificationRepository extends JpaRepository<UserClassificationEntity, UserClassificationId> {

    /**
     * 특정 사용자의 최근 N일 분류 결과 조회
     */
    @Query("SELECT u FROM UserClassificationEntity u WHERE u.subjectId = :subjectId AND u.classifiedDate > :startDate AND u.classifiedDate <= :endDate ORDER BY u.classifiedDate DESC")
    List<UserClassificationEntity> findRecentBySubjectId(
            @Param("subjectId") UUID subjectId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}
