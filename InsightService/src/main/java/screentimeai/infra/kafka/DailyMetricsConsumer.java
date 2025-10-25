package screentimeai.infra.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import screentimeai.domain.entity.DailyMetricsSaved;
import screentimeai.application.service.InsightPersistenceService;

import java.util.function.Consumer;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class DailyMetricsConsumer {

    private final InsightPersistenceService persistenceService;

    @Bean
    public Consumer<Message<DailyMetricsSaved>> consumeDailyMetricsSaved() {
        return message -> {
            DailyMetricsSaved event = message.getPayload();
            String eventType = (String) message.getHeaders().get("type");

            log.info("📥 Received DailyMetricsSaved event: type={}, subjectId={}, summaryDate={}, totalUsageMinutes={}",
                    eventType, event.getSubjectId(), event.getSummaryDate(), event.getTotalUsageMinutes());

            try {
                persistenceService.processDailyMetrics(event);
            } catch (Exception e) {
                log.error("Failed to process DailyMetricsSaved event: {}", event, e);
            }
        };
    }
}
