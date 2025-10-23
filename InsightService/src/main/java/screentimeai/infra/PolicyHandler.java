package screentimeai.infra;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import screentimeai.config.kafka.KafkaProcessor;
import screentimeai.domain.entity.Insight;
import screentimeai.domain.entity.DailyAnalysisDataPrepared;
import screentimeai.infra.repository.InsightRepository;
import org.springframework.cloud.stream.annotation.StreamListener;

//<<< Clean Arch / Inbound Adaptor
@Service
@Transactional
public class PolicyHandler {

    @Autowired
    InsightRepository insightRepository;

    @StreamListener(KafkaProcessor.INPUT)
    public void whatever(@Payload String eventString) {}

    @StreamListener(
        value = KafkaProcessor.INPUT,
        condition = "headers['type']=='DailyAnalysisDataPrepared'"
    )
    public void wheneverDailyAnalysisDataPrepared_GenerateInsights(
        @Payload DailyAnalysisDataPrepared dailyAnalysisDataPrepared
    ) {
        DailyAnalysisDataPrepared event = dailyAnalysisDataPrepared;
        System.out.println(
            "\n\n##### listener GenerateInsights : " +
            dailyAnalysisDataPrepared +
            "\n\n"
        );

        // Sample Logic //

        AnalyzeUserBehaviorCommand command = new AnalyzeUserBehaviorCommand();
        Insight.analyzeUserBehavior(command);
    }
}
//>>> Clean Arch / Inbound Adaptor
