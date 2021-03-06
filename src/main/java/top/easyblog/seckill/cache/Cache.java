package top.easyblog.seckill.cache;


import com.alibaba.fastjson.JSON;

/**
 * 定义公共缓存操作的接口
 * <p>
 * 由于缓存的一般用途，建议实现允许存储null值（例如缓存返回{@code null}的方法）
 *
 * @author Huang Xin
 * @date 2020/6/16 19:49
 */
public interface Cache {


    /**
     * 获取缓存实例id
     */
    String getInstanceId();

    /**
     * 获取缓存类型
     */
    String getCacheType();

    /**
     * 获取缓存名称
     */
    String getCacheName();

    /**
     * 获取实际缓存对象
     */
    Object getActualCache();

    /**
     * 获取指定key的缓存项
     */
    JSON get(String key);

    /**
     * 获取指定key的缓存项，并返回指定类型的返回值
     */
    default <T> T get(String key, Class<T> type) {
        Object value = get(key);
        if (null == value) {
            return null;
        }
        if (value != null && type != null && !type.isInstance(value)) {
            throw new IllegalStateException("Cached value is not of required type [" + type.getName() + "]: " + value);
        }
        return (T) value;
    }


    /**
     * 设置指定key的缓存项
     */
    void put(String key, JSON value);

    /**
     * 如果指定的key不存在，则设置缓存项，如果存在，则返回存在的值
     *
     * @see #put(String, JSON)
     */
    default Object putIfAbsent(String key, JSON value) {
        Object existingValue = get(key);
        if (existingValue == null) {
            put(key, value);
        }
        return existingValue;
    }


    /**
     * 删除指定的缓存项（如果存在）
     */
    void evict(String key);

    /**
     * 删除所有缓存项
     */
    void clear();

    /**
     * 检查key是否存在
     *
     * @return true 表示存在，false 表示不存在
     */
    boolean isExists(String key);

}
