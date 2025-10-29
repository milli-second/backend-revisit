package screentimeai.domain.entity;

import lombok.*;
import screentimeai.infra.AbstractEvent;

//<<< DDD / Domain Event
@Data
@ToString
public class LoggedIn extends AbstractEvent {

    private String subject_id;
    private String nickname;

    public LoggedIn(String subjectId, String nickname) {
        this.subject_id = subjectId;
        this.nickname = nickname;
    }

    public LoggedIn() {
        super();
    }
}
//>>> DDD / Domain Event
