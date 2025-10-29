package screentimeai.infra;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import screentimeai.infra.repository.UserRepository;

//<<< Clean Arch / Inbound Adaptor
@Service
@Transactional
public class PolicyHandler {

    @Autowired
    UserRepository userRepository;

}
//>>> Clean Arch / Inbound Adaptor
