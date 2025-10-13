package screentimeai.domain;
import lombok.Getter;

public class ProfileImageChanged {
    private final User user;

    public ProfileImageChanged(User user) {
        this.user = user;
    }
}
