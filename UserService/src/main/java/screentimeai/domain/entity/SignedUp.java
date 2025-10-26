package screentimeai.domain.entity;

import lombok.Data;
import lombok.ToString;
import screentimeai.infra.AbstractEvent;

//<<< DDD / Domain Event
@Data
@ToString
public class SignedUp extends AbstractEvent {
    //private Long id;
    private String subjectId;
    private String nickname;
    private String category;

    public SignedUp(String subjectId, String nickname, String category) {
        //this.id = id;
        this.subjectId = subjectId;
        this.nickname = nickname;
        this.category = category;
    }

    public SignedUp() {
        super();
    }
}
