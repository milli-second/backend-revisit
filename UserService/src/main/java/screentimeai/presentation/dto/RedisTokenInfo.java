package screentimeai.presentation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RedisTokenInfo {
    private String refreshToken;
    private String accessTokenJti;
}
