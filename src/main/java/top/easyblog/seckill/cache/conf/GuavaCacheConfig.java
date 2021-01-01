package top.easyblog.seckill.cache.conf;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import top.easyblog.seckill.cache.consts.CacheType;

import java.util.concurrent.TimeUnit;

/**
 * @author ：Huang Xin
 * @Description ： TODO
 * @data ：2020/12/18 20:21
 */
@Getter
@Setter
@Accessors(chain = true)
public class GuavaCacheConfig extends BaseCacheConfig {


    /**
     * 缓存类型
     *
     * @see CacheType
     */
    private String cacheType = CacheType.GUAVA.name();


    /**
     * 是否自动刷新过期缓存 true 表示是(默认)，false 表示否
     */
    private boolean autoRefreshExpireCache = true;

    /**
     * 缓存刷新调度线程池的大小
     */
    private Integer refreshPoolSize = Runtime.getRuntime().availableProcessors();

    /**
     * 缓存刷新的频率(秒)
     */
    private Long refreshPeriod = 30L;

    /**
     * 默认过期时间
     */
    private ExpireTime expireTime = new ExpireTime();

    /**
     * The spec to use to create caches. See CaffeineSpec for more details on the spec format.
     */
    private String defaultSpec;

    /**
     * 初始容量
     */
    private int initCapacity;

    /**
     * 最大容量
     */
    private int maxCapacity;

    /**
     * 最大并发度
     */
    private int maxConcurrencyLevel;

    @Setter
    @Getter
    public static class ExpireTime{
        private int expireTime = 30;
        private TimeUnit timeUnit=TimeUnit.SECONDS;

        public ExpireTime(){

        }

        public ExpireTime(int expireTime,TimeUnit timeUnit){
            this.expireTime=expireTime;
            this.timeUnit=timeUnit;
        }
    }


}
