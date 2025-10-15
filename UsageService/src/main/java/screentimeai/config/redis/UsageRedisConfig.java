package screentimeai.config.redis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class UsageRedisConfig {

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    @Value("${spring.data.redis.database:1}")
    private int redisDb;

    @Bean
    public LettuceConnectionFactory usageRedisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redisHost); // ✅ 환경변수에서 주입
        config.setPort(redisPort);
        config.setDatabase(redisDb);
        return new LettuceConnectionFactory(config);
    }

    @Bean(name = "usageRedisTemplate")
    public RedisTemplate<String, Object> usageRedisTemplate() {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(usageRedisConnectionFactory());

        // ✅ 문자열 key 직렬화
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        // ✅ JSON value 직렬화
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();

        // ✅ 모든 영역에서 동일한 직렬화기 사용
        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

}
