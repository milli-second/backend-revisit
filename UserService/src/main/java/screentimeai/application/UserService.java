package screentimeai.application;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import screentimeai.config.security.JwtUtil;
import screentimeai.domain.entity.User;
import screentimeai.infra.repository.UserRepository;
import screentimeai.presentation.dto.TokenInfo;

import java.util.concurrent.TimeUnit;

@Service
@Transactional
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;

    public void signup(String nickname, String rawPassword, Integer profileImageNumber) {
        if (userRepository.existsByNickname(nickname)) {
            throw new IllegalArgumentException("이미 사용 중인 닉네임입니다: " + nickname);
        }

        User user = new User();
        user.setNickname(nickname);
        user.setProfile_image_number(profileImageNumber);
        user.signup(rawPassword, passwordEncoder, profileImageNumber);
        userRepository.save(user);
        System.out.println("✅ User signup completed: " + nickname);
    }

    public TokenInfo login(String nickname, String rawPassword) {
        User user = userRepository.findByNickname(nickname)
                .orElseThrow(() -> new IllegalArgumentException("❌ 사용자 없음: " + nickname));
        user.login(rawPassword, passwordEncoder);

        // ✅ JWT 생성 (Access/Refresh 둘 다)
        TokenInfo tokenInfo = jwtUtil.generateTokens(user.getNickname(), user.getUuid());

        // ✅ Redis에 Refresh Token 저장 (유효기간 7일)
        redisTemplate.opsForValue().set(
                user.getUuid(),
                tokenInfo.getRefreshToken(),
                7,
                TimeUnit.DAYS
        );

        userRepository.save(user);
        System.out.println("✅ User login completed: " + nickname);
        return tokenInfo;
    }

    public void logout(String nickname) {
        User user = userRepository.findByNickname(nickname)
                .orElseThrow(() -> new IllegalArgumentException("❌ 사용자 없음: " + nickname));
        user.logout();

        userRepository.save(user);
        System.out.println("✅ User logout completed: " + nickname);
    }

    public void changeNickname(String nickname, String newNickname) {
        User user = userRepository.findByNickname(nickname)
                .orElseThrow(() -> new IllegalArgumentException("❌ 사용자 없음: " + nickname));

        if (userRepository.existsByNickname(newNickname)) {
            throw new IllegalArgumentException("❌ 이미 사용 중인 닉네임입니다: " + newNickname);
        }

        user.changeNickname(newNickname);
        userRepository.save(user);

        System.out.println("✅ Nickname changed from " + nickname + " → " + newNickname);
    }

    public void changePassword(String nickname, String oldPassword, String newPassword) {
        User user = userRepository.findByNickname(nickname)
                .orElseThrow(() -> new IllegalArgumentException("❌ 사용자 없음: " + nickname));

        user.changePassword(oldPassword, newPassword, passwordEncoder);
        userRepository.save(user);
        System.out.println("✅ Password changed for user: " + nickname);
    }

    @Transactional
    public void changeProfileImage(String nickname, int newProfileImageNumber) {
        User user = userRepository.findByNickname(nickname)
                .orElseThrow(() -> new IllegalArgumentException("❌ 사용자 없음: " + nickname));

        if (newProfileImageNumber < 1 || newProfileImageNumber > 8) {
            throw new IllegalArgumentException("프로필 이미지 번호는 1~8 사이여야 합니다.");
        }

        user.changeProfileImage(newProfileImageNumber);
        userRepository.save(user);

        System.out.println("🖼️ Profile image updated for user: " + nickname + " → " + newProfileImageNumber);
    }

    public void deactivateAccount(String nickname) {
        User user = userRepository.findByNickname(nickname)
                .orElseThrow(() -> new IllegalArgumentException("❌ 사용자 없음: " + nickname));
        user.deactivate();
        userRepository.save(user);
        System.out.println("🗑️ Account deactivated: " + nickname);
    }
}
