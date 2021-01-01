package top.easyblog.seckill.service;

import com.alibaba.fastjson.JSON;

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
    void set(String key, JSON value);


    /**
     * 从本地热点缓存中获取数据
     *
     * @param key
     * @return
     */
    JSON get(String key);

}
