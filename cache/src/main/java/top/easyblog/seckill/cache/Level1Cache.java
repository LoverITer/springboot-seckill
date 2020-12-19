package top.easyblog.seckill.cache;

/**
 * 一级缓存
 *
 * @author Huang Xin
 * @date 2020/6/30 10:54
 */
public interface Level1Cache<K,V> extends Cache<K,V> {

    /**
     * 缓存同步策略
     * 注：因为一级缓存为本地缓存，所以需要进行不同节点间的缓存数据同步
     */
    CacheSyncPolicy getCacheSyncPolicy();


    /**
     * 是否为 LoadingCache
     * 注意：如果是LoadingCache，则值由缓存自动加载，并存储在缓存中，直到被回收或手动失效，且LoadingCache一般会提供refresh()方法来刷新缓存。
     *
     * @see com.google.common.cache.LoadingCache
     */
    boolean isLoadingCache();

    /**
     * 清理本地缓存
     */
    void clearLocalCache(K key);

    /**
     * 异步加载{@code key}的新值
     * 当新值加载时，get操作将继续返回原值（如果有），除非将其删除;如果新值加载成功，则替换缓存中的前一个值。
     *
     * @see Level1Cache#isLoadingCache() 为true才能执行refresh方法
     */
    void refresh(K key);


    /**
     * 异步加载所有新值
     * 当新值加载时，get操作将继续返回原值（如果有），除非将其删除;如果新值加载成功，则替换缓存中的前一个值。
     *
     * @see Level1Cache#isLoadingCache() 为true才能执行该方法
     */
    void refreshAll();

    /**
     * 刷新过期缓存
     * 注：通过LoadingCache.get(key)来刷新过期缓存，若缓存未到过期时间则不刷新
     *
     * @see Level1Cache#isLoadingCache() 为true才能执行该方法
     */
    void refreshExpireCache(K key);

    /**
     * 刷新所有过期的缓存
     * 注：通过LoadingCache.get(key)来刷新过期缓存，若缓存未到过期时间则不刷新
     *
     * @see Level1Cache#isLoadingCache() 为true才能执行该方法
     */
    void refreshAllExpireCache();
}
