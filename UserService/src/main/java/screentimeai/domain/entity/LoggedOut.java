package screentimeai.domain.entity;

import lombok.*;
import screentimeai.infra.AbstractEvent;

//<<< DDD / Domain Event
@Data
@ToString
public class LoggedOut extends AbstractEvent {

    private Long id;
    private String nickname;

    public LoggedOut(User aggregate) {
        super(aggregate);
    }

    public LoggedOut() {
        super();
    }
}
//>>> DDD / Domain Event
