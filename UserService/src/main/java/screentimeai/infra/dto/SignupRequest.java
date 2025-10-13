package screentimeai.infra.dto;

import jakarta.validation.constraints.*;

public class SignupRequest {

    @NotBlank(message = "닉네임은 필수입니다.")
    @Pattern(
            regexp = "^[a-zA-Z0-9]{5,20}$",
            message = "닉네임은 영문과 숫자 조합으로 5~20자 이내여야 합니다."
    )
    private String nickname;

    @NotNull(message = "프로필 이미지는 반드시 선택해야 합니다.")
    @Min(value = 1, message = "프로필 번호는 1~8 사이여야 합니다.")
    @Max(value = 8, message = "프로필 번호는 1~8 사이여야 합니다.")
    private Integer profileImageNumber; // ✅ Integer로 변경!


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
