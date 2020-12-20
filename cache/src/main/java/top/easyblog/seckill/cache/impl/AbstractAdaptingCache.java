package top.easyblog.seckill.cache.impl;

import top.easyblog.seckill.cache.Cache;
import top.easyblog.seckill.cache.conf.BaseCacheConfig;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 缓存适配器
 * @author Huang Xin
 */
public abstract class AbstractAdaptingCache implements Cache{


    /**
     * 缓存实例id
     */
    private  String instanceId;
    /**
     * 缓存名字
     */
    private  String cacheName;

    /**
     * NullValue的过期时间，单位秒
     */
    private long nullValueExpireTimeSeconds;

    public AbstractAdaptingCache(String cacheName, BaseCacheConfig cacheConfig) {
        this.instanceId = cacheConfig.getInstanceId();
        this.cacheName = cacheName;
        if (this.nullValueExpireTimeSeconds < 0) {
            this.nullValueExpireTimeSeconds = 60;
        }
    }


    @Override
    public String getInstanceId() {
        return this.instanceId;
    }

    @Override
    public String getCacheName() {
        return this.cacheName;
    }

}
