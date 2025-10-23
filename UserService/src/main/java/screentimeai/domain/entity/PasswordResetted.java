package screentimeai.domain.entity;

import lombok.*;
import screentimeai.infra.AbstractEvent;

//<<< DDD / Domain Event
@Data
@ToString
public class PasswordResetted extends AbstractEvent {

    private Long id;
    private String nickname;

    public PasswordResetted(User aggregate) {
        super(aggregate);
    }

    public PasswordResetted() {
        super();
    }
}
//>>> DDD / Domain Event
