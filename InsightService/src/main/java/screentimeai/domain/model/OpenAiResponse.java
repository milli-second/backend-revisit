package screentimeai.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OpenAiResponse {

    private List<Choice> choices;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Choice {
        private Message message;
        private Integer index;
        private String finish_reason;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {
        private String role;
        private String content;
    }
}
