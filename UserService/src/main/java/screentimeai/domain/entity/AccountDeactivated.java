package screentimeai.domain.entity;

import lombok.Data;
import lombok.ToString;
import screentimeai.infra.AbstractEvent;

@Data
@ToString
public class AccountDeactivated extends AbstractEvent {
    private Long id;
    private String nickname;

    public AccountDeactivated(User user) {
        super(user);
        this.id = user.getId();
        this.nickname = user.getNickname();
    }

    public AccountDeactivated() {
        super();
    }
}
