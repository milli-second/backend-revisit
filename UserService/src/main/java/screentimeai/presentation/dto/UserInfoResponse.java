package screentimeai.presentation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoResponse {
    private String nickname;
    private String subjectId;
    private int profileImageNumber;
    private String category;
}
