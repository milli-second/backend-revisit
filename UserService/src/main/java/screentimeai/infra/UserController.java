package screentimeai.infra;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import screentimeai.application.UserService;
import screentimeai.domain.User;
import screentimeai.config.security.JwtUtil;
import screentimeai.infra.dto.LoginRequest;
import screentimeai.infra.dto.SignupRequest;
import screentimeai.infra.dto.ChangePasswordRequest;
import screentimeai.infra.dto.ChangeNicknameRequest;
import screentimeai.infra.dto.ChangeProfileImageRequest;
import java.util.Map;

@RestController
@RequestMapping(value="/users")
public class UserController {
    private final UserService userService;
    private final JwtUtil jwtUtil;

    public UserController(UserService userService, JwtUtil jwtUtil) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping("/test")
    public String test() {
        return "UserService is running!";
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        String token = userService.login(request.getNickname(), request.getPassword());

        //User user = userService.login(request.getNickname(), request.getPassword());
        //String token = jwtUtil.generateToken(user.getNickname());

        return ResponseEntity.ok(Map.of(
                "token", token,
                "nickname", request.getNickname()
        ));
    }

    @PostMapping("/logout")
    @SecurityRequirement(name = "JWT Authentication")
    public ResponseEntity<String> logout(
            @Parameter(hidden = true) @RequestHeader("Authorization") String token) {
        // Bearer 제거
        String accessToken = token.replace("Bearer ", "");

        // JWT에서 닉네임 추출
        String nickname = jwtUtil.getUsernameFromToken(accessToken);
        userService.logout(nickname);
        return ResponseEntity.ok("로그아웃 완료");
    }

    @PostMapping("/signup")
    public String signup(@Valid @RequestBody SignupRequest request) {
        userService.signup(request.getNickname(), request.getPassword(),request.getProfileImageNumber());
        return "User signed up: " + request.getNickname();
    }

    // ✅ 닉네임 변경
    @PatchMapping("/change/nickname")
    @SecurityRequirement(name = "JWT Authentication")
    public ResponseEntity<String> changeNickname(
            @Parameter(hidden = true) @RequestHeader("Authorization") String token,
            @Valid @RequestBody ChangeNicknameRequest request
    ) {
        // 1️⃣ 토큰에서 기존 닉네임 추출
        String accessToken = token.replace("Bearer ", "");
        String currentNickname = jwtUtil.getUsernameFromToken(accessToken);
        System.out.println("nickname from token(change-nickname): " + currentNickname);

        // 2️⃣ 서비스 호출
        userService.changeNickname(currentNickname, request.getNewNickname());
        return ResponseEntity.ok("닉네임이 성공적으로 변경되었습니다.");
    }


    // ✅ 비밀번호 변경
    @PostMapping("/change/password")
    @SecurityRequirement(name = "JWT Authentication")
    public ResponseEntity<String> changePassword(
            @Parameter(hidden = true) @RequestHeader("Authorization") String token,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        // 1️⃣ 토큰에서 닉네임 추출
        String accessToken = token.replace("Bearer ", "");
        String nickname = jwtUtil.getUsernameFromToken(accessToken);
        System.out.println("nickname from token(change-password): " + nickname);

        // 2️⃣ 서비스 호출
        userService.changePassword(nickname, request.getOldPassword(), request.getNewPassword());

        return ResponseEntity.ok("비밀번호가 성공적으로 변경되었습니다.");
    }

    // ✅ 프로필 이미지 변경
    @PatchMapping("/change/profile-image")
    @SecurityRequirement(name = "JWT Authentication")
    public ResponseEntity<String> changeProfileImage(
            @Parameter(hidden = true) @RequestHeader("Authorization") String token,
            @Valid @RequestBody ChangeProfileImageRequest request
    ) {
        // 1️⃣ 토큰에서 닉네임 추출
        String accessToken = token.replace("Bearer ", "");
        String nickname = jwtUtil.getUsernameFromToken(accessToken);
        System.out.println("nickname from token(change-profile-image): " + nickname);

        // 2️⃣ 서비스 호출 (→ 내부에서 Redis 인증)
        userService.changeProfileImage(nickname, request.getProfileImageNumber());

        return ResponseEntity.ok("프로필 이미지가 성공적으로 변경되었습니다.");
    }
}
