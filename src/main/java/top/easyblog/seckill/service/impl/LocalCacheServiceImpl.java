package top.easyblog.seckill.service.impl;


import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import top.easyblog.seckill.cache.Cache;
import top.easyblog.seckill.cache.CacheSyncPolicy;
import top.easyblog.seckill.cache.builder.impl.GuavaCacheBuilder;
import top.easyblog.seckill.service.LocalCacheService;

import javax.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;

/**
 * @author ：huangxin
 * @modified ：
 * @since ：2020/12/17 09:43
 */
@Slf4j
@Service
public final class LocalCacheServiceImpl implements LocalCacheService {


    private Cache commentCache = null;

    @Autowired
    private CacheSyncPolicy cacheSyncPolicy;


    @PostConstruct
    public void init() {
        GuavaCacheBuilder cacheBuilder = GuavaCacheBuilder.newBuilder();
        //缓存的初始容量
        commentCache = cacheBuilder.initialCapacity(16)
                //缓存的最大容量
                .maximumSize(256)
                //最大操作缓存
                .concurrencyLevel(10)
                //缓存失效时间
                .expireAfterWrite(5, TimeUnit.MINUTES)
                //本地缓存同步策略
                .cacheSyncPolicy(cacheSyncPolicy)
                .build("guava-local-cache-1");
    }


    @Override
    public void set(String key, JSON value) {
        if (!StringUtils.isEmpty(key) && !StringUtils.isEmpty(value)) {
            commentCache.put(key, value);
        }
    }


    @Override
    public JSON get(String key) {
        if (!StringUtils.isEmpty(key)) {
            return commentCache.get(key);
        }
        return null;
    }
}
