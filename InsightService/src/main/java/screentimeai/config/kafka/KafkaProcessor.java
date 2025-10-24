package screentimeai.config.kafka;

import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.SubscribableChannel;

public interface KafkaProcessor {
    String INPUT = "event-in";
    String OUTPUT = "event-out";

//    @Input(INPUT)
//    SubscribableChannel inboundTopic();
//
//    @Output(OUTPUT)
//    MessageChannel outboundTopic();
}
