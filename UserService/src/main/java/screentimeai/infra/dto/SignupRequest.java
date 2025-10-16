package screentimeai.infra.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(description = "회원가입 요청 DTO")
public class SignupRequest {

    @Schema(description = "닉네임", example = "testuser123")
    @NotBlank(message = "닉네임은 필수입니다.")
    @Pattern(
            regexp = "^[a-zA-Z0-9]{5,20}$",
            message = "닉네임은 영문과 숫자 조합으로 5~20자 이내여야 합니다."
    )
    private String nickname;

    @Schema(description = "프로필 이미지 번호", example = "3")
    @NotNull(message = "프로필 이미지는 반드시 선택해야 합니다.")
    @Min(value = 1, message = "프로필 번호는 1~8 사이여야 합니다.")
    @Max(value = 8, message = "프로필 번호는 1~8 사이여야 합니다.")
    private Integer profileImageNumber; // ✅ Integer로 변경!

    @Schema(description = "비밀번호", example = "Password123!")
    @NotBlank(message = "비밀번호는 필수입니다.")
    @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,20}$",
            message = "비밀번호는 8~20자이며, 영문, 숫자, 특수문자를 모두 포함해야 합니다."
    )
    private String password;

    public String getNickname() {
        return nickname;
    }

    public String getPassword() {
        return password;
    }
    public Integer getProfileImageNumber() {
        return profileImageNumber;
    }
}
