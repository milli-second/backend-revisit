package screentimeai.infra;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class AbstractEvent {
    String eventType;
    Long timestamp;

    public AbstractEvent() {
        this.setEventType(this.getClass().getSimpleName());
        this.timestamp = System.currentTimeMillis();
    }
}
