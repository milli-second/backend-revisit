////commit 후에만 Kafka발행
//package screentimeai.infra.kafka;
//
//import lombok.RequiredArgsConstructor;
//import org.springframework.stereotype.Component;
//import org.springframework.transaction.event.TransactionalEventListener;
//import org.springframework.transaction.event.TransactionPhase;
//import screentimeai.domain.events.ScreenDataSavedEvent;
//
//@Component
//@RequiredArgsConstructor
//public class ScreenDataEventHandler {
//
//    private final EventPublisher publisher;
//
//    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
//    public void on(ScreenDataSavedEvent event) {
//        // 커밋 성공 후에만 발행되므로 DB-카프카 불일치 없음
//        publisher.publishScreenDataEvent("screenData-out-0", event.getSaved());
//    }
//}
