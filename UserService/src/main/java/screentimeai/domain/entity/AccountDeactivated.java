package screentimeai.domain.entity;

import lombok.Data;
import lombok.ToString;
import screentimeai.infra.AbstractEvent;

@Data
@ToString
public class AccountDeactivated extends AbstractEvent {
    private String subject_id;
    private String nickname;

    public AccountDeactivated(String subject_id, String nickname) {
        this.subject_id = subject_id;
        this.nickname = nickname;
    }

    public AccountDeactivated() {
        super();
    }
}
