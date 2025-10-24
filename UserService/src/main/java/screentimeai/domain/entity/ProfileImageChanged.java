package screentimeai.domain.entity;

import lombok.Data;
import lombok.ToString;
import screentimeai.infra.AbstractEvent;

@Data
@ToString
public class ProfileImageChanged extends AbstractEvent {
    private String subject_id;
    private int profile_image_number;

    public ProfileImageChanged(String subjectId, int profileImageNumber) {
        this.subject_id = subjectId;
        this.profile_image_number = profileImageNumber;
    }

    public ProfileImageChanged() {
        super();
    }
}
