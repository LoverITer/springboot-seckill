package top.easyblog.seckill.cache.builder.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import top.easyblog.seckill.cache.Cache;
import top.easyblog.seckill.cache.CacheSupport;
import top.easyblog.seckill.cache.CacheSyncPolicy;
import top.easyblog.seckill.cache.builder.CacheBuilder;
import top.easyblog.seckill.cache.conf.GuavaCacheConfig;
import top.easyblog.seckill.cache.impl.GuavaLocalCache;
import top.easyblog.seckill.cache.sync.RocketMQLocalCacheSyncPolicy;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author ：Huang Xin
 * @Description ： GuavaCache建造者：用于构建一个基于谷歌Guava Cache的本地缓存
 * @data ：2020/12/18 16:53
 */
public class GuavaCacheBuilder implements CacheBuilder {


    /**
     * 全局唯一的缓存个数计数器
     */
    private static final AtomicInteger CACHE_COUNTER =new AtomicInteger(0);


    private CacheSupport cacheSupport=new CacheSupport();


    /**
     * 默认缓存配置
     */
    private static final int DEFAULT_INITIAL_CAPACITY = 16;
    private static final int DEFAULT_CONCURRENCY_LEVEL = 5;
    private static final int DEFAULT_EXPIRATION = 10;
    private static final long DEFAULT_REFRESH = 10;
    private static final int DEFAULT_MAX_CAPACITY = 100;
    private static final CacheSyncPolicy DEFAULT_CACHE_SYNC_POLICY = new RocketMQLocalCacheSyncPolicy();

    /**
     * Google Guava 缓存
     */
    private static GuavaCacheConfig guavaConfig = new GuavaCacheConfig();

    /**
     * 构造缓存建造器
     *
     * @return
     */
    public static  GuavaCacheBuilder newBuilder() {
        guavaConfig.setInstanceId("GUAVA-CACHE-"+ CACHE_COUNTER.incrementAndGet());
        guavaConfig.setInitCapacity(DEFAULT_INITIAL_CAPACITY);
        guavaConfig.setMaxCapacity(DEFAULT_MAX_CAPACITY);
        guavaConfig.setExpireTime(new GuavaCacheConfig.ExpireTime(DEFAULT_EXPIRATION,TimeUnit.SECONDS));
        guavaConfig.setMaxConcurrencyLevel(DEFAULT_CONCURRENCY_LEVEL);
        guavaConfig.setRefreshPeriod(DEFAULT_REFRESH);
        guavaConfig.setCacheSyncPolicy(DEFAULT_CACHE_SYNC_POLICY);
        guavaConfig.setAutoRefreshExpireCache(true);
        return new GuavaCacheBuilder();
    }


    @Override
    public Cache build(String cacheName) {
        GuavaLocalCache guavaLocalCache = new GuavaLocalCache(cacheName, guavaConfig);
        cacheSupport.setCache(guavaConfig.getInstanceId(), guavaLocalCache);
        return guavaLocalCache;
    }


    public GuavaCacheBuilder initialCapacity(int initCapacity) {
        guavaConfig.setInitCapacity(initCapacity);
        return this;
    }

    public GuavaCacheBuilder maximumSize(int maximumSize){
        guavaConfig.setMaxCapacity(maximumSize);
        return this;
    }

    public GuavaCacheBuilder concurrencyLevel(int concurrencyLevel) {
        guavaConfig.setMaxConcurrencyLevel(concurrencyLevel);
        return this;
    }

    public GuavaCacheBuilder expireAfterWrite(int expireTime, TimeUnit timeUnit) {
        guavaConfig.setExpireTime(new GuavaCacheConfig.ExpireTime(expireTime,timeUnit));
        return this;
    }

    public GuavaCacheBuilder refreshPeriod(long refreshPeriod) {
        guavaConfig.setRefreshPeriod(refreshPeriod);
        return this;
    }

    public GuavaCacheBuilder autoRefreshExpireCache(boolean autoRefreshExpireCache) {
        guavaConfig.setAutoRefreshExpireCache(autoRefreshExpireCache);
        return this;
    }


    public GuavaCacheBuilder cacheSyncPolicy(CacheSyncPolicy syncPolicy){
        guavaConfig.setCacheSyncPolicy(syncPolicy);
        return this;
    }

}
