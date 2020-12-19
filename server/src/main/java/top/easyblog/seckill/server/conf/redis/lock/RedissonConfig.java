package top.easyblog.seckill.server.conf.redis.lock;

import org.springframework.context.annotation.Configuration;

/**
 * redisson通用化配置
 *
 * @Author:debug (SteadyJack)
 * @Date: 2019/7/2 10:57
 **/
@Configuration
public class RedissonConfig {

 /*   @Autowired
    private Environment env;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + env.getProperty("spring.redis.host") + ":" + env.getProperty("spring.redis.port"))
                .setPassword(env.getProperty("spring.redis.password"))
                .setDatabase(0)
                .setConnectionPoolSize(512)
                .setTimeout(5000)
                .setConnectTimeout(15000)
                .setRetryAttempts(5)
                .setRetryInterval(1500)
                .setPingConnectionInterval(50000);
        return Redisson.create(config);
    }
*/

}