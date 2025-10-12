package screentimeai.infra;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import screentimeai.application.UserService;
import screentimeai.domain.User;
import screentimeai.config.security.JwtUtil;
import screentimeai.infra.dto.LoginRequest;
import screentimeai.infra.dto.SignupRequest;

import java.util.Map;

@RestController
@RequestMapping(value="/api/v1/users")
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
    public ResponseEntity<String> logout(@RequestHeader("Authorization") String token) {
        // Bearer 제거
        String accessToken = token.replace("Bearer ", "");

        // JWT에서 닉네임 추출
        String nickname = jwtUtil.getUsernameFromToken(accessToken);
        userService.logout(nickname);
        return ResponseEntity.ok("로그아웃 완료");

    }

    @PostMapping("/signup")
    public String signup(@Valid @RequestBody SignupRequest request) {
        userService.signup(request.getNickname(), request.getPassword());
        return "User signed up: " + request.getNickname();
    }

    // ✅ 비밀번호 변경
    @PostMapping("/change-password")
    public ResponseEntity<String> changePassword(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, String> body
    ) {
        // 1️⃣ 토큰에서 닉네임 추출
        String accessToken = token.replace("Bearer ", "");
        String nickname = jwtUtil.getUsernameFromToken(accessToken);
        System.out.println("nickname from token(change-password): " + nickname);

        // 2️⃣ 요청 JSON 파싱
        String oldPassword = body.get("oldPassword");
        String newPassword = body.get("newPassword");

        // 3️⃣ 서비스 호출
        userService.changePassword(nickname, oldPassword, newPassword);

        return ResponseEntity.ok("비밀번호가 성공적으로 변경되었습니다.");
    }
}
