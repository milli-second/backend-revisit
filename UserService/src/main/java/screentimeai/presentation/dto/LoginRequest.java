package screentimeai.presentation.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "로그인 요청")
public class LoginRequest {

    @Schema(description = "닉네임", example = "testuser123")
    @NotBlank(message = "닉네임은 필수입니다.")
    private String nickname;

    @Schema(description = "비밀번호", example = "Password123!")
    @NotBlank(message = "비밀번호는 필수입니다.")
    private String password;

    public String getNickname() {
        return nickname;
    }
    public String getPassword() {
        return password;
    }
}
