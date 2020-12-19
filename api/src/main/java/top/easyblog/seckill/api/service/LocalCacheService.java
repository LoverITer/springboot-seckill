package top.easyblog.seckill.api.service;

/**
 * 本地热点缓存
 *
 * @author ：huangxin
 * @modified ：
 * @since ：2020/12/17 09:41
 */
public interface LocalCacheService {

    /**
     * 向本地热点缓存中存放数据
     *
     * @param key
     * @param value
     * @return
     */
    void set(String key, Object value);

    /**
     * 从本地热点缓存中获取数据
     *
     * @param key
     * @return
     */
    Object get(String key);

}
