package screentimeai.infra.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "프로필 이미지 변경 요청")
public class ChangeProfileImageRequest {

    @Schema(description = "프로필 이미지 번호", example = "5")
    @NotNull(message = "프로필 이미지 번호는 필수입니다.")
    @Min(value = 1, message = "프로필 번호는 1~8 사이여야 합니다.")
    @Max(value = 8, message = "프로필 번호는 1~8 사이여야 합니다.")
    private Integer profileImageNumber;

    public Integer getProfileImageNumber() {
        return profileImageNumber;
    }

    public void setProfileImageNumber(Integer profileImageNumber) {
        this.profileImageNumber = profileImageNumber;
    }
}
