package screentimeai.config.redis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class AuthRedisConfig {
    @Bean
    public LettuceConnectionFactory authRedisConnectionFactory() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory("localhost", 6379);
        factory.setDatabase(0); // ✅ 인증용 Redis (DB 0) index 0임
        return factory;
    }

    @Bean(name = "authRedisTemplate")
    public RedisTemplate<String, String> authRedisTemplate() {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(authRedisConnectionFactory());
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        return template;
    }
}

