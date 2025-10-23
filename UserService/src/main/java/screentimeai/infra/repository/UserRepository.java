package screentimeai.infra.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import screentimeai.domain.entity.User;

import java.util.Optional;

////<<< PoEAA / Repository
public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByNickname(String nickname); // 중복 체크용
    Optional<User> findByNickname(String nickname); // ✅ 유저 조회용
    Optional<User> findByUuid(String uuid); // ✅ UUID로 유저 조회용
}