package screentimeai.infra;

import jakarta.transaction.Transactional;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

//<<< Clean Arch / Inbound Adaptor
@Service
@Transactional
public class PolicyHandler {

    @Bean
    public Consumer<String> eventConsumer() {
        return eventString -> {
            // Handle incoming events
            System.out.println("Received event: " + eventString);
        };
    }
}
//>>> Clean Arch / Inbound Adaptor
