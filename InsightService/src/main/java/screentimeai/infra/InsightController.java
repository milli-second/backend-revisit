package screentimeai.infra;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;
import screentimeai.infra.repository.InsightRepository;

//<<< Clean Arch / Inbound Adaptor

@RestController
// @RequestMapping(value="/insights")
@Transactional
public class InsightController {

    @Autowired
    InsightRepository insightRepository;
}
//>>> Clean Arch / Inbound Adaptor
