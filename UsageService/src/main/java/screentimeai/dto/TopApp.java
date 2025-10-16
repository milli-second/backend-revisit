package screentimeai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopApp {
    private int rank;
    private String appName;
    private String packageName;
    private int usageMinutes;
}
