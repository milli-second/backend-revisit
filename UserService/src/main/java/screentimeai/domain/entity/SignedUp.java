package screentimeai.domain.entity;

import lombok.*;
import screentimeai.infra.AbstractEvent;

//<<< DDD / Domain Event
@Data
@ToString
public class SignedUp extends AbstractEvent {
    private Long id;
    private String uuid;      // ✅ 새로 추가
    private String nickname;
    private String category;//초기에는 null처리
    private String password;

    public SignedUp(User user) {
        this.id = user.getId();
        this.uuid = user.getUuid(); // ✅ UUID 포함

        this.nickname = user.getNickname();
        this.category = null;
        this.password = user.getPassword();
    }

    public SignedUp() {
        super();
    }
}
//>>> DDD / Domain Event
