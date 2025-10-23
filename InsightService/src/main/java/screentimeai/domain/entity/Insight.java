package screentimeai.domain.entity;

import java.util.List;

import jakarta.persistence.*;
import lombok.Data;
import screentimeai.InsightserviceApplication;
import screentimeai.infra.repository.InsightRepository;

@Entity
@Table(name = "Insight_table")
@Data
//<<< DDD / Aggregate Root
public class Insight {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @ElementCollection
    private List<String> insightReport;

    @PostPersist
    public void onPostPersist() {
        InsightGenerated insightGenerated = new InsightGenerated(this);
        insightGenerated.publishAfterCommit();
    }

    public static InsightRepository repository() {
        InsightRepository insightRepository = InsightserviceApplication.applicationContext.getBean(
            InsightRepository.class
        );
        return insightRepository;
    }
}
//>>> DDD / Aggregate Root
