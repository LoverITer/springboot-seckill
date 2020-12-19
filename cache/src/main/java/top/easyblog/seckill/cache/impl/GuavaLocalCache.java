package top.easyblog.seckill.cache.impl;


import com.google.common.cache.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.Message;
import top.easyblog.seckill.cache.CacheSyncPolicy;
import top.easyblog.seckill.cache.Level1Cache;
import top.easyblog.seckill.cache.conf.BaseCacheConfig;
import top.easyblog.seckill.cache.conf.GuavaCacheConfig;
import top.easyblog.seckill.cache.consts.CacheOpt;
import top.easyblog.seckill.cache.consts.CacheType;
import top.easyblog.seckill.cache.schedule.RefreshExpiredCacheTask;
import top.easyblog.seckill.cache.schedule.RefreshSupport;
import top.easyblog.seckill.cache.sync.CacheMessage;
import top.easyblog.seckill.cache.utils.RandomUtil;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * 本地缓存实现类 GuavaLocalCache，底层基于Google Guava Cache实现
 *
 * @author chenck
 * @date 2020/6/29 16:55
 */
@Slf4j
public class GuavaLocalCache<K,V>  extends AbstractAdaptingCache<K,V> implements Level1Cache<K,V> {

    /**
     * 缓存同步策略
     */
    private final CacheSyncPolicy cacheSyncPolicy;

    /**
     * L1 Guava Cache
     */
    private Cache<K,V> commonCache;


    public GuavaLocalCache(String cacheName, BaseCacheConfig cacheConfig) {
        super(cacheName, cacheConfig);
        //同步策略就是本地缓存同时使用的中间件以及策略
        this.cacheSyncPolicy = cacheConfig.getCacheSyncPolicy();
        CacheBuilder<K,V> cacheBuilder = (CacheBuilder<K, V>) CacheBuilder.newBuilder();
        GuavaCacheConfig guavaCacheConf=(GuavaCacheConfig)cacheConfig;
        cacheBuilder.initialCapacity(guavaCacheConf.getInitCapacity());
        cacheBuilder.concurrencyLevel(guavaCacheConf.getMaxConcurrencyLevel());
        cacheBuilder.maximumSize(guavaCacheConf.getMaxCapacity());
        cacheBuilder.expireAfterWrite(guavaCacheConf.getExpireTime().getExpireTime(), TimeUnit.SECONDS);
        cacheBuilder.removalListener(notification -> {
            final RemovalCause cause = notification.getCause();
            switch (cause) {
                //缓存到期
                case EXPIRED:
                    //缓存大小限制
                case SIZE:
                    //缓存被垃圾回收
                case COLLECTED:
                    //如果是缓存到期等原因被删除，则需要通知分布式环境下的其他机器也要删除
                    CacheSyncPolicy cacheSyncPolicy = guavaCacheConf.getCacheSyncPolicy();
                    cacheSyncPolicy.publish(createMessage(notification.getKey(), CacheOpt.CACHE_CLEAR));
                    break;
                //缓存显示删除（这里没有调用是避免事件循环）
                case EXPLICIT:
                    //缓存显示替换（这了没有调用是避免事件循环）
                case REPLACED:
                    break;
                default:
                    log.error("there should not be [{}]", cause);
            }
        });
        this.commonCache = cacheBuilder.build();
        if (guavaCacheConf.isAutoRefreshExpireCache()) {
            // 定期刷新过期的缓存
            RefreshSupport.getInstance(guavaCacheConf.getRefreshPoolSize())
                    .scheduleWithFixedDelay(new RefreshExpiredCacheTask(this), 5,
                            guavaCacheConf.getRefreshPeriod(), TimeUnit.SECONDS);
        }
    }

    @Override
    public String getCacheType() {
        return CacheType.GUAVA.name().toLowerCase();
    }
    

    @Override
    public Cache<K,V> getActualCache() {
        return this.commonCache;
    }

    @Override
    public CacheSyncPolicy getCacheSyncPolicy() {
        return this.cacheSyncPolicy;
    }


    @Override
    public boolean isLoadingCache() {
        return this.commonCache instanceof LoadingCache;
    }


    @Override
    public void put(K key, V value) {
        commonCache.put(key, value);
        if (null != cacheSyncPolicy) {
            //本地缓存放入缓存，通过MQ通知其他节点同步
            cacheSyncPolicy.publish(createMessage(key, CacheOpt.CACHE_REFRESH));
        }
    }

    @Override
    public V get(K key) {
        return this.commonCache.getIfPresent(key);
    }

    @Override
    public void evict(K key) {
        log.debug("GuavaCache evict cache, cacheName={}, key={}", this.getCacheName(), key);
        commonCache.invalidate(key);
        if (null != cacheSyncPolicy) {
            //本地缓存失效，通过MQ通知其他节点同步
            cacheSyncPolicy.publish(createMessage(key, CacheOpt.CACHE_CLEAR));
        }
    }

    @Override
    public void clear() {
        log.debug("GuavaCache clear cache, cacheName={}", this.getCacheName());
        commonCache.invalidateAll();
        if (null != cacheSyncPolicy) {
            //本地缓存被清理，通知其他节点也清理
            cacheSyncPolicy.publish(createMessage(null, CacheOpt.CACHE_CLEAR));
        }
    }

    @Override
    public boolean isExists(K key) {
        boolean rslt = commonCache.asMap().containsKey(key);
        log.debug("[GuavaCache] key is exists, cacheName={}, key={}, rslt={}", this.getCacheName(), key, rslt);
        return rslt;
    }

    @Override
    public void clearLocalCache(K key) {
        log.info("GuavaCache clear local cache, cacheName={}, key={}", this.getCacheName(), key);
        if (key == null) {
            commonCache.invalidateAll();
        } else {
            commonCache.invalidate(key);
        }
    }

    @Override
    public void refresh(K key) {
        if (isLoadingCache()) {
            log.debug("GuavaCache refresh cache, cacheName={}, key={}", this.getCacheName(), key);
            ((LoadingCache) commonCache).refresh(key);
        }
    }

    @Override
    public void refreshAll() {
        if (isLoadingCache()) {
            LoadingCache loadingCache = (LoadingCache) commonCache;
            for (Object key : loadingCache.asMap().keySet()) {
                log.debug("GuavaCache refreshAll cache, cacheName={}, key={}", this.getCacheName(), key);
                loadingCache.refresh(key);
            }
        }
    }

    @Override
    public void refreshExpireCache(Object key) {
        if (isLoadingCache()) {
            log.debug("GuavaCache refreshExpireCache, cacheName={}, key={}", this.getCacheName(), key);
            try {
                // 通过LoadingCache.get(key)来刷新过期缓存
                ((LoadingCache) commonCache).get(key);
            } catch (ExecutionException e) {
                log.error("GuavaCache refreshExpireCache error, cacheName=" + this.getCacheName() + ", key=" + key, e);
            }
        }
    }

    @Override
    public void refreshAllExpireCache() {
        if (isLoadingCache()) {
            LoadingCache loadingCache = (LoadingCache) commonCache;
            for (Object key : loadingCache.asMap().keySet()) {
                log.debug("[GuavaCache] refreshAllExpireCache, cacheName={}, key={}", this.getCacheName(), key);
                try {
                    // 通过LoadingCache.get(key)来刷新过期缓存
                    loadingCache.get(key);
                } catch (ExecutionException e) {
                    log.error("[GuavaCache] refreshAllExpireCache error, cacheName=" + this.getCacheName() + ", key=" + key, e);
                }
            }

        }
    }

    private CacheMessage createMessage(Object key, String optType) {
        return new CacheMessage()
                .setInstanceId(RandomUtil.getUUID())
                .setCacheName(this.getInstanceId())
                .setKey(key)
                .setOptType(optType);
    }

}
