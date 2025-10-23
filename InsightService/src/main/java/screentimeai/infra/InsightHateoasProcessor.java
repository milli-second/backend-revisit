package screentimeai.infra;

import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelProcessor;
import org.springframework.stereotype.Component;
import screentimeai.domain.entity.Insight;

@Component
public class InsightHateoasProcessor
    implements RepresentationModelProcessor<EntityModel<Insight>> {

    @Override
    public EntityModel<Insight> process(EntityModel<Insight> model) {
        return model;
    }
}
