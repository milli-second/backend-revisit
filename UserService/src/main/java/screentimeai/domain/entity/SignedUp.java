package screentimeai.domain.entity;

import lombok.Data;
import lombok.ToString;
import screentimeai.infra.AbstractEvent;

//<<< DDD / Domain Event
@Data
@ToString
public class SignedUp extends AbstractEvent {
    //private Long id;
    private String subject_id;
    private String nickname;
    private String category;

    public SignedUp(String subject_id, String nickname, String category) {
        //this.id = id;
        this.subject_id = subject_id;
        this.nickname = nickname;
        this.category = category;
    }

    public SignedUp() {
        super();
    }
}
