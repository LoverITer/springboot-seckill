package top.easyblog.seckill.conf.redis;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.joda.time.DateTime;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import top.easyblog.seckill.conf.redis.serializer.JodaDataTimeDeserializer;
import top.easyblog.seckill.conf.redis.serializer.JodaDataTimeSerializer;

/**
 * @author Huanxin
 */
public class StringObjectRedisTemplate extends RedisTemplate<String, Object> {


    public StringObjectRedisTemplate() {
        //普通key-value的序列化方式
        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();
        this.setKeySerializer(stringRedisSerializer);
        Jackson2JsonRedisSerializer jackson2JsonSerializer = new Jackson2JsonRedisSerializer(Object.class);
        //将自定义的时间序列化方式注册到ObjectMapper中
        ObjectMapper objectMapper = new ObjectMapper();
        SimpleModule simpleModule = new SimpleModule();
        simpleModule.addDeserializer(DateTime.class, new JodaDataTimeDeserializer());
        simpleModule.addSerializer(DateTime.class, new JodaDataTimeSerializer());
        objectMapper.registerModule(simpleModule);
        jackson2JsonSerializer.setObjectMapper(objectMapper);
        this.setValueSerializer(jackson2JsonSerializer);
        //针对Hash特别指定他的序列化方式
        this.setHashKeySerializer(stringRedisSerializer);
        this.setHashValueSerializer(jackson2JsonSerializer);
    }

    public StringObjectRedisTemplate(RedisConnectionFactory connectionFactory) {
        this();
        this.setConnectionFactory(connectionFactory);
        this.afterPropertiesSet();
    }

    @Override
    protected RedisConnection preProcessConnection(RedisConnection connection, boolean existingConnection) {
        return super.preProcessConnection(connection, existingConnection);
    }

}
