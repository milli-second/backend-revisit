//package screentimeai.infra.kafka;
//
//import org.springframework.context.annotation.Bean;
//import screentimeai.domain.DailyAnalysisDataPrepared;
//import java.util.function.Consumer;
//
//public class EventConsumer {
//    @Bean
//    public Consumer<DailyAnalysisDataPrepared> consumeScreenDataEvent() {
//        return event -> {
//            System.out.println("📥 Event received: " + event.getUserId());
//            // TODO: DB 저장/비즈니스 로직
//        };
//    }
//}
