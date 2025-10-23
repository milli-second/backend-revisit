package screentimeai.domain.entity;

import lombok.*;
import screentimeai.infra.AbstractEvent;

//<<< DDD / Domain Event
@Data
@ToString
public class LoggedIn extends AbstractEvent {

    private Long id;
    private String nickname;

    public LoggedIn(User aggregate) {
        super(aggregate);
        this.id = aggregate.getId();
        this.nickname = aggregate.getNickname();
    }

    public LoggedIn() {
        super();
    }
}
//>>> DDD / Domain Event
