package screentimeai.infra.kafka;

public interface KafkaProcessor {
    String INPUT = "event-in";
    String OUTPUT = "event-out";

//    @Input(INPUT)
//    SubscribableChannel inboundTopic();
//
//    @Output(OUTPUT)
//    MessageChannel outboundTopic();
}
