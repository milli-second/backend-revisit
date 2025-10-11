//package screentimeai.infra.kafka;
//
//import org.springframework.cloud.stream.function.StreamBridge;
//import org.springframework.context.annotation.Bean;
//import org.springframework.messaging.MessageHeaders;
//import org.springframework.messaging.support.MessageBuilder;
//import org.springframework.stereotype.Service;
//import org.springframework.util.MimeTypeUtils;
//import screentimeai.domain.DailyAnalysisDataPrepared;
//import java.util.function.Consumer;
//
//@Service
//public class EventPublisher {
//    private final StreamBridge streamBridge;
//    public EventPublisher(StreamBridge streamBridge) {
//        this.streamBridge = streamBridge;
//    }
//
//    public void publishScreenDataEvent(String bindingName, Object payload){
//        boolean sent = streamBridge.send(
//                bindingName,
//                MessageBuilder.withPayload(payload)
//                        .setHeader(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_JSON)
//                        .setHeader("type", payload.getClass().getSimpleName())
//                        .build()
//        );
//        System.out.println("📤 Event published to " + bindingName + ", success=" + sent);
//    }
//}
