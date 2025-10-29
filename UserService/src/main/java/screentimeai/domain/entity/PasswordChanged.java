package screentimeai.domain.entity;

import lombok.Data;
import lombok.ToString;
import screentimeai.infra.AbstractEvent;

@Data
@ToString
public class PasswordChanged extends AbstractEvent {
    private String nickname;
    private String subject_id;

    public PasswordChanged(String subjectId, String nickname) {
        this.subject_id = subjectId;
        this.nickname = nickname;
    }

    public PasswordChanged() {
        super();
    }
}
