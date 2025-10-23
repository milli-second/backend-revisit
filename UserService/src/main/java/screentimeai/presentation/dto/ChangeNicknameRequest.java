package screentimeai.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "닉네임 변경 요청")
public class ChangeNicknameRequest {

    @Schema(description = "새 닉네임", example = "newuser456")
    @NotBlank(message = "새 닉네임은 필수입니다.")
    @Pattern(
            regexp = "^[a-zA-Z0-9]{5,20}$",
            message = "닉네임은 영문과 숫자 조합으로 5~20자 이내여야 합니다."
    )
    private String newNickname;

    public String getNewNickname() {
        return newNickname;
    }

    public void setNewNickname(String newNickname) {
        this.newNickname = newNickname;
    }
}
