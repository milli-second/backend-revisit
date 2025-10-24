package screentimeai.presentation.controller;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import screentimeai.application.UserService;
import screentimeai.config.security.JwtUtil;
import screentimeai.presentation.dto.*;

import screentimeai.presentation.dto.UserInfoResponse;
import java.util.Map;

@RestController
@RequestMapping(value = "/users")
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
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        TokenInfo tokenInfo = userService.login(request.getNickname(), request.getPassword());

        ResponseCookie cookie = ResponseCookie.from("refreshToken", tokenInfo.getRefreshToken())
                .maxAge(7 * 24 * 60 * 60) // 7일
                .path("/")
                .secure(true)
                .httpOnly(true)
                .build();
        response.setHeader("Set-Cookie", cookie.toString());

        return ResponseEntity.ok(Map.of(
                "accessToken", tokenInfo.getAccessToken(),
                "nickname", request.getNickname()
        ));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@CookieValue(name = "refreshToken") String refreshToken, HttpServletResponse response) {
        TokenInfo tokenInfo = userService.reissueTokens(refreshToken);

        ResponseCookie cookie = ResponseCookie.from("refreshToken", tokenInfo.getRefreshToken())
                .maxAge(7 * 24 * 60 * 60) // 7일
                .path("/")
                .secure(true)
                .httpOnly(true)
                .build();
        response.setHeader("Set-Cookie", cookie.toString());

        return ResponseEntity.ok(Map.of(
                "accessToken", tokenInfo.getAccessToken(),
                "nickname", jwtUtil.getUsernameFromToken(tokenInfo.getAccessToken())
        ));
    }

    @PostMapping("/logout")
    @SecurityRequirement(name = "JWT Authentication")
    public ResponseEntity<String> logout(
            @Parameter(hidden = true) @RequestHeader("Authorization") String token) {
        // Bearer 제거
        String accessToken = token.replace("Bearer ", "");

        // JWT에서 닉네임 추출
        userService.logout(accessToken);
        return ResponseEntity.ok("로그아웃 완료");
    }

    @PostMapping("/signup")
    public String signup(@Valid @RequestBody SignupRequest request) {
        userService.signup(request.getNickname(), request.getPassword(), request.getProfileImageNumber());
        return "User signed up: " + request.getNickname();
    }

    // ✅ 닉네임 변경
    @PatchMapping("/change/nickname")
    @SecurityRequirement(name = "JWT Authentication")
    public ResponseEntity<String> changeNickname(
            @Parameter(hidden = true) @RequestHeader("Authorization") String token,
            @Valid @RequestBody ChangeNicknameRequest request
    ) {
        // 1️⃣ 토큰에서 기존 닉네임 추출 (이제 서비스에서 처리)
        String accessToken = token.replace("Bearer ", "");

        // 2️⃣ 서비스 호출
        userService.changeNickname(accessToken, request.getNewNickname());
        return ResponseEntity.ok("닉네임이 성공적으로 변경되었습니다.");
    }


    // ✅ 비밀번호 변경
    @PostMapping("/change/password")
    @SecurityRequirement(name = "JWT Authentication")
    public ResponseEntity<String> changePassword(
            @Parameter(hidden = true) @RequestHeader("Authorization") String token,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        // 1️⃣ 토큰에서 닉네임 추출 (이제 서비스에서 처리)
        String accessToken = token.replace("Bearer ", "");

        // 2️⃣ 서비스 호출
        userService.changePassword(accessToken, request.getOldPassword(), request.getNewPassword());

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

    // ✅ 내 정보 조회
    @GetMapping("/me")
    @SecurityRequirement(name = "JWT Authentication")
    public ResponseEntity<UserInfoResponse> getUserInfo(
            @Parameter(hidden = true) @RequestHeader("Authorization") String token) {
        String accessToken = token.replace("Bearer ", "");
        UserInfoResponse userInfo = userService.getUserInfo(accessToken);
        return ResponseEntity.ok(userInfo);
    }
}
