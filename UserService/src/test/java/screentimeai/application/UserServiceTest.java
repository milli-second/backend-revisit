package screentimeai.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import screentimeai.domain.User;
import screentimeai.domain.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder encoder;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    @DisplayName("회원가입에 성공한다.")
    @Test
    void signup_success() {
        // given
        String nickname = "testuser";
        String password = "password123";
        int profileImageNumber = 1;

        // when
        userService.signup(nickname, password, profileImageNumber);

        // then
        User user = userRepository.findByNickname(nickname).orElseThrow();
        assertThat(user.getNickname()).isEqualTo(nickname);
        assertTrue(encoder.matches(password, user.getPassword()));
        assertThat(user.getProfile_image_number()).isEqualTo(profileImageNumber);
        assertTrue(user.isActive());
    }

    @DisplayName("중복된 닉네임으로 회원가입에 실패한다.")
    @Test
    void signup_fail_with_duplicate_nickname() {
        // given
        String nickname = "testuser";
        String password = "password123";
        int profileImageNumber = 1;
        userService.signup(nickname, password, profileImageNumber);

        // when & then
        assertThatThrownBy(() -> userService.signup(nickname, "newpassword", 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 사용 중인 닉네임입니다:");
    }

    @DisplayName("로그인에 성공하고 JWT 토큰을 반환한다.")
    @Test
    void login_success() {
        // given
        String nickname = "testuser";
        String password = "password123";
        int profileImageNumber = 1;
        userService.signup(nickname, password, profileImageNumber);

        // when
        String token = userService.login(nickname, password);

        // then
        assertNotNull(token);
    }

    @DisplayName("잘못된 비밀번호로 로그인에 실패한다.")
    @Test
    void login_fail_with_wrong_password() {
        // given
        String nickname = "testuser";
        String password = "password123";
        int profileImageNumber = 1;
        userService.signup(nickname, password, profileImageNumber);

        // when & then
        assertThatThrownBy(() -> userService.login(nickname, "wrongpassword"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("❌ 비밀번호 불일치");
    }

    @DisplayName("닉네임 변경에 성공한다.")
    @Test
    void changeNickname_success() {
        // given
        String nickname = "testuser";
        String newNickname = "newuser";
        String password = "password123";
        int profileImageNumber = 1;
        userService.signup(nickname, password, profileImageNumber);
        userService.login(nickname, password);

        // when
        userService.changeNickname(nickname, newNickname);

        // then
        User user = userRepository.findByNickname(newNickname).orElseThrow();
        assertThat(user.getNickname()).isEqualTo(newNickname);
    }

    @DisplayName("비밀번호 변경에 성공한다.")
    @Test
    void changePassword_success() {
        // given
        String nickname = "testuser";
        String oldPassword = "old123";
        String newPassword = "new123";
        int profileImageNumber = 1;
        userService.signup(nickname, oldPassword, profileImageNumber);
        userService.login(nickname, oldPassword);

        // when
        userService.changePassword(nickname, oldPassword, newPassword);

        // then
        User user = userRepository.findByNickname(nickname).orElseThrow();
        assertTrue(encoder.matches(newPassword, user.getPassword()));
    }

    @DisplayName("프로필 이미지 변경에 성공한다.")
    @Test
    void changeProfileImage_success() {
        // given
        String nickname = "testuser";
        String password = "password123";
        int profileImageNumber = 1;
        int newProfileImageNumber = 2;
        userService.signup(nickname, password, profileImageNumber);
        userService.login(nickname, password);

        // when
        userService.changeProfileImage(nickname, newProfileImageNumber);

        // then
        User user = userRepository.findByNickname(nickname).orElseThrow();
        assertThat(user.getProfile_image_number()).isEqualTo(newProfileImageNumber);
    }

    @DisplayName("회원 탈퇴에 성공한다.")
    @Test
    void deactivateAccount_success() {
        // given
        String nickname = "testuser";
        String password = "password123";
        int profileImageNumber = 1;
        userService.signup(nickname, password, profileImageNumber);

        // when
        userService.deactivateAccount(nickname);

        // then
        User user = userRepository.findByNickname(nickname).orElseThrow();
        assertFalse(user.isActive());
    }
}
