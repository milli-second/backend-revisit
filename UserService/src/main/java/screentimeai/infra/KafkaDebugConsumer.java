package screentimeai.infra;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import java.util.Base64;

@Component
public class KafkaDebugConsumer {
    @KafkaListener(topics = "user_login", groupId = "debug")
    public void listenLogin(String message) {
        System.out.println("📥 [user_login] Received raw: " + message);
    }
    @KafkaListener(topics = "user_signup", groupId = "debug")
    public void listenSignup(String message) {
        System.out.println("📥 [user_signup] Received: " + message);
    }

    @KafkaListener(topics = "user_logout", groupId = "debug")
    public void listenLogout(String message) {
        System.out.println("📥 [user_logout] Received: " + message);
    }
}