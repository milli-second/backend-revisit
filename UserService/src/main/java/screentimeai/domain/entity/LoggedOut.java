package screentimeai.domain.entity;

import lombok.*;
import screentimeai.infra.AbstractEvent;

//<<< DDD / Domain Event
@Data
@ToString
public class LoggedOut extends AbstractEvent {

    private String subject_id;
    private String nickname;

    public LoggedOut(String subjectId, String nickname) {
        this.subject_id = subjectId;
        this.nickname = nickname;
    }

    public LoggedOut() {
        super();
    }
}
//>>> DDD / Domain Event
