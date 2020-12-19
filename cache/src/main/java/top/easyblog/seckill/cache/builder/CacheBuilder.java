package top.easyblog.seckill.cache.builder;

import top.easyblog.seckill.cache.Cache;

/**
 * @author ：Huang Xin
 * @Description ： 用于构建一个本地缓存实例
 * @data ：2020/12/18 16:49
 */
public interface CacheBuilder<K, V> {

    /**
     * 构建一个本地缓存实例
     *
     * @return   本地缓存实例
     */
    Cache<K, V> build(String cacheName);


}
