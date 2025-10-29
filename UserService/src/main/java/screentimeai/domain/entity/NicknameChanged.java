package screentimeai.domain.entity;

import lombok.Data;
import lombok.ToString;
import screentimeai.infra.AbstractEvent;

@Data
@ToString
public class NicknameChanged extends AbstractEvent {
    private String subject_id;
    private String nickname;

    public NicknameChanged(String subjectId, String nickname) {
        this.subject_id = subjectId;
        this.nickname = nickname;
    }

    public NicknameChanged() {
        super();
    }
}
