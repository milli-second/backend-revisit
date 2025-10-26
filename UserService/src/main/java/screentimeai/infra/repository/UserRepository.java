package screentimeai.infra.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import screentimeai.domain.entity.User;

import java.util.Optional;

////<<< PoEAA / Repository
public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByNickname(String nickname); // 중복 체크용
    Optional<User> findByNickname(String nickname); // ✅ 유저 조회용
    Optional<User> findBySubjectId(String subjectId); // ✅ subjectId로 유저 조회용
}