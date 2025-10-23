package screentimeai.infra.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import screentimeai.domain.entity.Insight;

//<<< PoEAA / Repository
@RepositoryRestResource(collectionResourceRel = "insights", path = "insights")
public interface InsightRepository
    extends PagingAndSortingRepository<Insight, Long> {
    @Query(
        value = "select insight " +
        "from Insight insight " +
        "where(:id is null or insight.id = :id)"
    )
    Insight viewInsightReport(Long id);
}
