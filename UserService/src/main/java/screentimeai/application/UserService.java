package screentimeai.application;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import screentimeai.domain.*;
import screentimeai.config.security.JwtUtil;

import java.util.concurrent.TimeUnit;


@Service
@Transactional
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, String> redisTemplate;
    private final JwtUtil jwtUtil; // ✅ JwtUtil도 주입받기


    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, @Qualifier("authRedisTemplate") RedisTemplate<String,String> redisTemplate, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.redisTemplate = redisTemplate;
        this.jwtUtil = jwtUtil;

    }

    public void signup(String nickname, String rawPassword,Integer profileImageNumber) {
        if (userRepository.existsByNickname(nickname)) {
            throw new IllegalArgumentException("이미 사용 중인 닉네임입니다: " + nickname);
        }

        User user = new User();
        user.setNickname(nickname);
        //user.setUuid(UUID.randomUUID().toString());
        user.setProfile_image_number(profileImageNumber);
        user.signup(rawPassword, passwordEncoder,profileImageNumber);
        userRepository.save(user); // 👉 save + flush에서 DomainEvent 발행
        System.out.println("✅ User signup completed: " + nickname);
    }

    public String login(String nickname, String rawPassword) {
        User user = userRepository.findByNickname(nickname)
                .orElseThrow(() -> new IllegalArgumentException("❌ 사용자 없음: " + nickname));
        user.login(rawPassword, passwordEncoder);

        // ✅ JWT 생성
        String token = jwtUtil.generateToken(user.getNickname());
        // ✅ Redis에 저장 (key: access:<nickname>)
        redisTemplate.opsForValue().set(
                "access:" + user.getNickname(),
                token,
                10, TimeUnit.HOURS // JWT 만료와 동일한 TTL
        );

        userRepository.save(user); // 👉 이벤트 발행을 트리거
        System.out.println("✅ User login completed: " + nickname);
        return token;
    }

    public void logout(String nickname) {
        User user = userRepository.findByNickname(nickname)
                .orElseThrow(() -> new IllegalArgumentException("❌ 사용자 없음: " + nickname));
        user.logout();

        // ✅ Redis에 저장된 access 토큰 삭제
        String redisKey = "access:" + nickname;
        Boolean deleted = redisTemplate.delete(redisKey);

        if (deleted) {
            System.out.println("🧹 Redis 토큰 삭제 완료 → 로그아웃 성공 (" + nickname + ")");
        } else {
            System.out.println("⚠️ Redis에 해당 토큰이 없음 (" + nickname + ")");
        }

        userRepository.save(user); // 👉 이벤트 발행을 트리거
        System.out.println("✅ User logout completed: " + nickname);
    }

    public void changeNickname(String nickname, String newNickname) {
        User user = userRepository.findByNickname(nickname)
                .orElseThrow(() -> new IllegalArgumentException("❌ 사용자 없음: " + nickname));

        // ✅ Redis에서 토큰 유효성 검증
        String redisKey = "access:" + nickname;
        String storedToken = redisTemplate.opsForValue().get(redisKey);

        if (storedToken == null) {
            throw new IllegalStateException("❌ 유효한 세션(토큰)이 없습니다. 다시 로그인해주세요.");
        }

        if (!jwtUtil.validateToken(storedToken, user.getNickname())) {
            throw new IllegalStateException("❌ 토큰이 만료되었거나 유효하지 않습니다.");
        }

        // ✅ 닉네임 변경 (중복 체크 포함)
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

        // 2️⃣ Redis에서 현재 유효한 토큰 확인
        String redisKey = "access:" + nickname;
        String storedToken = redisTemplate.opsForValue().get(redisKey);

        if (storedToken == null) {
            throw new IllegalStateException("❌ 유효한 세션(토큰)이 없습니다. 다시 로그인해주세요.");
        }

        // 3️⃣ Redis에 있는 토큰이 실제로 JWT 유효한지 검증
        if (!jwtUtil.validateToken(storedToken, user.getNickname())) {
            throw new IllegalStateException("❌ 토큰이 만료되었거나 유효하지 않습니다.");
        }
        user.changePassword(oldPassword, newPassword, passwordEncoder);
        userRepository.save(user); // 👉 이벤트 발행됨
        System.out.println("✅ Password changed for user: " + nickname);
    }


    @Transactional
    public void changeProfileImage(String nickname, int newProfileImageNumber) {
        User user = userRepository.findByNickname(nickname)
                .orElseThrow(() -> new IllegalArgumentException("❌ 사용자 없음: " + nickname));

        // ✅ Redis에서 토큰 존재 확인
        String redisKey = "access:" + nickname;
        String storedToken = redisTemplate.opsForValue().get(redisKey);

        if (storedToken == null) {
            throw new IllegalStateException("❌ 유효한 세션(토큰)이 없습니다. 다시 로그인해주세요.");
        }

        // ✅ Redis 토큰이 실제 JWT 유효한지 검증
        if (!jwtUtil.validateToken(storedToken, user.getNickname())) {
            throw new IllegalStateException("❌ 토큰이 만료되었거나 유효하지 않습니다.");
        }

        // ✅ 요청된 이미지 번호 유효성 검사
        if (newProfileImageNumber < 1 || newProfileImageNumber > 8) {
            throw new IllegalArgumentException("프로필 이미지 번호는 1~8 사이여야 합니다.");
        }

        // ✅ 실제 변경 처리
        user.changeProfileImage(newProfileImageNumber);
        userRepository.save(user);

        System.out.println("🖼️ Profile image updated for user: " + nickname + " → " + newProfileImageNumber);
    }


    public void deactivateAccount(String nickname) {
        User user = userRepository.findByNickname(nickname)
                .orElseThrow(() -> new IllegalArgumentException("❌ 사용자 없음: " + nickname));
        user.deactivate();
        userRepository.save(user); // 👉 이벤트 발행 트리거
        System.out.println("🗑️ Account deactivated: " + nickname);
    }
}





