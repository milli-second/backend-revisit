package screentimeai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableFeignClients
@EnableScheduling
public class
UsageserviceApplication {

    public static ApplicationContext applicationContext;

    public static void main(String[] args) {
        applicationContext =
                SpringApplication.run(UsageserviceApplication.class, args);
    }
}
