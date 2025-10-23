package screentimeai.infra;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import screentimeai.application.AbstractEventPublisher;
import screentimeai.domain.entity.*;

@Component
public class UserEventHandler {

    private final AbstractEventPublisher eventPublisher;

    public UserEventHandler(AbstractEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @TransactionalEventListener
    public void handle(LoggedIn event) {
        System.out.println("🎯 Handling LoggedIn event: " + event.getNickname());
        eventPublisher.publish("eventProducer-out-2", event, "LoggedIn");
    }

    @TransactionalEventListener
    public void handle(LoggedOut event) {
        System.out.println("🎯 Handling LoggedOut event: " + event.getNickname());
        eventPublisher.publish("eventProducer-out-1", event, "LoggedOut");
    }

    @TransactionalEventListener
    public void handle(SignedUp event) {
        System.out.println("🎯 Handling SignedUp event: " + event.getNickname());
        eventPublisher.publish("eventProducer-out-0", event, "SignedUp");
    }

    @TransactionalEventListener
    public void handle(PasswordChanged event) {
        System.out.println("🎯Handling SignedUp event: " + event.getNickname());
        eventPublisher.publish("eventProducer-out-3", event, "PasswordChanged");
    }

    @TransactionalEventListener
    public void handle(AccountDeactivated event) {
        System.out.println("🎯Handling SignedUp event: " + event.getNickname());
        eventPublisher.publish("eventProducer-out-4", event, "PasswordChanged");
    }
}
