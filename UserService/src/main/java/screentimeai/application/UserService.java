package screentimeai.application;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import screentimeai.config.security.JwtUtil;
import screentimeai.domain.entity.User;
import screentimeai.infra.repository.UserRepository;


@Service
@Transactional
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;


    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
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
        String token = jwtUtil.generateToken(user.getNickname(), user.getUuid());

        userRepository.save(user); // 👉 이벤트 발행을 트리거
        System.out.println("✅ User login completed: " + nickname);
        return token;
    }

    public void logout(String nickname) {
        User user = userRepository.findByNickname(nickname)
                .orElseThrow(() -> new IllegalArgumentException("❌ 사용자 없음: " + nickname));
        user.logout();

        userRepository.save(user); // 👉 이벤트 발행을 트리거
        System.out.println("✅ User logout completed: " + nickname);
    }

    public void changeNickname(String nickname, String newNickname) {
        User user = userRepository.findByNickname(nickname)
                .orElseThrow(() -> new IllegalArgumentException("❌ 사용자 없음: " + nickname));

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

        user.changePassword(oldPassword, newPassword, passwordEncoder);
        userRepository.save(user); // 👉 이벤트 발행됨
        System.out.println("✅ Password changed for user: " + nickname);
    }


    @Transactional
    public void changeProfileImage(String nickname, int newProfileImageNumber) {
        User user = userRepository.findByNickname(nickname)
                .orElseThrow(() -> new IllegalArgumentException("❌ 사용자 없음: " + nickname));

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





