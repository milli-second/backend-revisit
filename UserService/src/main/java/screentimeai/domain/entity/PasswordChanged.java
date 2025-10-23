package screentimeai.domain.entity;

import lombok.Data;
import lombok.ToString;
import screentimeai.infra.AbstractEvent;

@Data
@ToString
public class PasswordChanged extends AbstractEvent {
    private Long id;
    private String nickname;

    public PasswordChanged(User user) {
        this.id = user.getId();
        this.nickname = user.getNickname();
    }

    public PasswordChanged() {
        super();
    }
}
