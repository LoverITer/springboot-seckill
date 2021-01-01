package top.easyblog.seckill.cache.conf;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import top.easyblog.seckill.cache.CacheSyncPolicy;
import top.easyblog.seckill.cache.sync.RocketMQLocalCacheSyncPolicy;

/**
 * @author chenck
 * @date 2020/6/30 17:19
 */
@Getter
@Setter
@Accessors(chain = true)
public abstract class BaseCacheConfig {


    /**
     * 缓存实例id
     */
    private String instanceId;

    /**
     * 缓存同步策略，默认使用RocketMQ来广播消息
     */
    private CacheSyncPolicy cacheSyncPolicy = new RocketMQLocalCacheSyncPolicy();


}
