package top.easyblog.seckill.cache.consts;

/**
 * 缓存同步策略类型
 *
 * @author chenck
 * @date 2020/7/7 16:31
 */
public enum CacheSyncPolicyType {
    /**
     * 使用Redis同步
     */
    REDIS,
    /**
     * 使用kafka同步
     */
    KAFKA,
    /**
     * 使用RokcetMQ同步
     */
    ROCKETMQ;

    public static CacheSyncPolicyType getCacheType(String type) {
        CacheSyncPolicyType[] types = CacheSyncPolicyType.values();
        for (CacheSyncPolicyType cacheType : types) {
            if (cacheType.name().equalsIgnoreCase(type)) {
                return cacheType;
            }
        }
        return null;
    }
}
