package screentimeai.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import screentimeai.config.security.JwtUtil;
import screentimeai.domain.entity.User;
import screentimeai.infra.repository.UserRepository;
import screentimeai.presentation.dto.RedisTokenInfo;
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
    private final ObjectMapper objectMapper; // ObjectMapper 주입

    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";

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

        TokenInfo tokenInfo = jwtUtil.generateTokens(user.getNickname(), user.getUuid());

        try {
            // RedisTokenInfo 객체 생성
            RedisTokenInfo redisTokenInfo = RedisTokenInfo.builder()
                    .refreshToken(tokenInfo.getRefreshToken())
                    .accessTokenJti(jwtUtil.getJtiFromToken(tokenInfo.getAccessToken()))
                    .build();

            // Redis에 JSON 문자열로 저장 (유효기간 7일)
            redisTemplate.opsForValue().set(
                    user.getUuid(),
                    objectMapper.writeValueAsString(redisTokenInfo),
                    7,
                    TimeUnit.DAYS
            );
        } catch (JsonProcessingException e) {
            throw new RuntimeException("RedisTokenInfo 직렬화 오류", e);
        }

        userRepository.save(user);
        System.out.println("✅ User login completed: " + nickname);
        return tokenInfo;
    }

    public TokenInfo reissueTokens(String refreshToken) {
        // 1. Refresh Token 유효성 검증
        if (jwtUtil.isTokenExpired(refreshToken)) {
            throw new IllegalArgumentException("❌ 만료된 Refresh Token 입니다.");
        }

        // 2. Token에서 사용자 UUID 추출
        String uuid = jwtUtil.getUuidFromToken(refreshToken);

        // 3. Redis에서 저장된 Refresh Token 정보 조회
        String storedTokenInfoJson = redisTemplate.opsForValue().get(uuid);

        RedisTokenInfo storedRedisTokenInfo;
        try {
            storedRedisTokenInfo = (storedTokenInfoJson != null) ?
                    objectMapper.readValue(storedTokenInfoJson, RedisTokenInfo.class) : null;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("RedisTokenInfo 역직렬화 오류", e);
        }

        // 4. 재사용 탐지: Redis에 토큰이 없거나, 요청된 토큰과 일치하지 않는 경우
        if (storedRedisTokenInfo == null || !storedRedisTokenInfo.getRefreshToken().equals(refreshToken)) {
            // 누군가 이미 해당 리프레시 토큰을 사용하여 재발급 받았고, 탈취로 간주
            redisTemplate.delete(uuid); // 해당 유저의 모든 세션 강제 종료

            // ✅ 공격자가 사용 중인 Access Token의 jti를 블랙리스트에 추가
            if (storedRedisTokenInfo != null && storedRedisTokenInfo.getAccessTokenJti() != null) {
                long remainingValidity = jwtUtil.getExpirationDateFromToken(jwtUtil.generateAccessToken("temp", uuid)).getTime() - System.currentTimeMillis();
                // Access Token의 남은 유효기간만큼 블랙리스트에 등록
                if (remainingValidity > 0) {
                    redisTemplate.opsForValue().set(
                            BLACKLIST_PREFIX + storedRedisTokenInfo.getAccessTokenJti(),
                            "true",
                            remainingValidity,
                            TimeUnit.MILLISECONDS
                    );
                }
            }
            throw new SecurityException("🚨 비정상적인 토큰 재발급 요청입니다. 모든 세션이 종료됩니다.");
        }

        User user = userRepository.findByUuid(uuid)
                .orElseThrow(() -> new IllegalArgumentException("❌ 사용자를 찾을 수 없습니다."));

        // 5. 새로운 토큰 생성 (회전)
        TokenInfo newTokenInfo = jwtUtil.generateTokens(user.getNickname(), user.getUuid());

        try {
            // 6. Redis에 새로운 Refresh Token 정보 저장
            RedisTokenInfo newRedisTokenInfo = RedisTokenInfo.builder()
                    .refreshToken(newTokenInfo.getRefreshToken())
                    .accessTokenJti(jwtUtil.getJtiFromToken(newTokenInfo.getAccessToken()))
                    .build();

            redisTemplate.opsForValue().set(
                    uuid,
                    objectMapper.writeValueAsString(newRedisTokenInfo),
                    7,
                    TimeUnit.DAYS
            );
        } catch (JsonProcessingException e) {
            throw new RuntimeException("RedisTokenInfo 직렬화 오류", e);
        }

        System.out.println("✅ Token reissued for user: " + user.getNickname());
        return newTokenInfo;
    }


    public void logout(String accessToken) {
        String uuid = jwtUtil.getUuidFromToken(accessToken);
        User user = userRepository.findByUuid(uuid)
                .orElseThrow(() -> new IllegalArgumentException("❌ 사용자 없음: " + uuid));
        user.logout();

        // ✅ Redis에서 Refresh Token 정보 삭제
        redisTemplate.delete(user.getUuid());

        // ✅ Access Token의 jti를 블랙리스트에 추가
        String jti = jwtUtil.getJtiFromToken(accessToken);
        if (jti != null) {
            long expiration = jwtUtil.getExpirationDateFromToken(accessToken).getTime();
            long currentTime = System.currentTimeMillis();
            long remainingValidity = expiration - currentTime;

            if (remainingValidity > 0) {
                redisTemplate.opsForValue().set(
                        "jwt:blacklist:" + jti,
                        "true",
                        remainingValidity,
                        TimeUnit.MILLISECONDS
                );
            }
        }

        userRepository.save(user);
        System.out.println("✅ User logout completed: " + user.getNickname());
    }

    public void changeNickname(String accessToken, String newNickname) {
        String uuid = jwtUtil.getUuidFromToken(accessToken);
        User user = userRepository.findByUuid(uuid)
                .orElseThrow(() -> new IllegalArgumentException("❌ 사용자 없음: " + uuid));

        if (userRepository.existsByNickname(newNickname)) {
            throw new IllegalArgumentException("❌ 이미 사용 중인 닉네임입니다: " + newNickname);
        }

        user.changeNickname(newNickname);
        userRepository.save(user);

        System.out.println("✅ Nickname changed from " + user.getNickname() + " → " + newNickname);
    }

    public void changePassword(String accessToken, String oldPassword, String newPassword) {
        String uuid = jwtUtil.getUuidFromToken(accessToken);
        User user = userRepository.findByUuid(uuid)
                .orElseThrow(() -> new IllegalArgumentException("❌ 사용자 없음: " + uuid));

        user.changePassword(oldPassword, newPassword, passwordEncoder);
        userRepository.save(user);
        // ✅ 비밀번호 변경 시 Refresh Token 무효화 (모든 세션 종료)
        redisTemplate.delete(user.getUuid());
        System.out.println("✅ Password changed for user: " + user.getNickname());
    }

    @Transactional
    public void changeProfileImage(String accessToken, int newProfileImageNumber) {
        String uuid = jwtUtil.getUuidFromToken(accessToken);
        User user = userRepository.findByUuid(uuid)
                .orElseThrow(() -> new IllegalArgumentException("❌ 사용자 없음: " + uuid));

        if (newProfileImageNumber < 1 || newProfileImageNumber > 8) {
            throw new IllegalArgumentException("프로필 이미지 번호는 1~8 사이여야 합니다.");
        }

        user.changeProfileImage(newProfileImageNumber);
        userRepository.save(user);

        System.out.println("🖼️ Profile image updated for user: " + user.getNickname() + " → " + newProfileImageNumber);
    }

    public void deactivateAccount(String nickname) {
        User user = userRepository.findByNickname(nickname)
                .orElseThrow(() -> new IllegalArgumentException("❌ 사용자 없음: " + nickname));
        user.deactivate();
        userRepository.save(user);
        System.out.println("🗑️ Account deactivated: " + nickname);
    }
}
