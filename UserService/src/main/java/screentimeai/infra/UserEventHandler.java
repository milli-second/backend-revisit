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
        eventPublisher.publish("user_login", event, "LoggedIn");
    }

    @TransactionalEventListener
    public void handle(LoggedOut event) {
        System.out.println("🎯 Handling LoggedOut event: " + event.getNickname());
        eventPublisher.publish("user_logout", event, "LoggedOut");
    }

    @TransactionalEventListener
    public void handle(SignedUp event) {
        System.out.println("🎯 Handling SignedUp event: " + event.getNickname());
        eventPublisher.publish("user_signup", event, "SignedUp");
    }

    @TransactionalEventListener
    public void handle(NicknameChanged event) {
        System.out.println("🎯Handling Change Nickname event: " + event.getNickname());
        eventPublisher.publish("user_nickname_changed", event, "NicknameChanged");
    }

    @TransactionalEventListener
    public void handle(PasswordChanged event) {
        System.out.println("🎯Handling SignedUp event: " + event.getNickname());
        eventPublisher.publish("user_change_password", event, "PasswordChanged");
    }

    @TransactionalEventListener
    public void handle(AccountDeactivated event) {
        System.out.println("🎯Handling Deactivate event: " + event.getNickname());
        eventPublisher.publish("user_deactivate", event, "PasswordChanged");
    }
}
