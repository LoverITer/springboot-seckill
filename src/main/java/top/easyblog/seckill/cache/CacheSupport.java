package top.easyblog.seckill.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import top.easyblog.seckill.cache.sync.CacheMessage;
import top.easyblog.seckill.cache.sync.ConcurrentLinkedHashMap;
import top.easyblog.seckill.cache.utils.RandomUtil;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author ：Huang Xin
 * @Description ： 将一个实例化的本地记录到本地HashMap中，便于管理
 * @data ：2020/12/18 21:09
 */
@Slf4j
@Component
public class CacheSupport {

    private static int MESSAGE_RECORD_MAP_MAX_SIZE=512;

    /**
     * 缓存容器
     * Map<String,Map<cacheName,Cache>>
     */
    private static final Map<String, Cache> CACHE_INSTANCE_MAP = new ConcurrentHashMap<>(32);


    /**
     * 消息的发送记录.
     *
     * Tips: 因为所有节点都订阅了同一频道，也会接听到自身广播的事件，所以节点在响应事件时，可以做幂等处理
     */
    private  final Map<String, CacheMessage> MESSAGE_MAP =new ConcurrentLinkedHashMap<>(MESSAGE_RECORD_MAP_MAX_SIZE);



    /**
     * 获取缓存实例 Cache
     */
    public  Cache getCache(String cacheInstanceId) {
        return CACHE_INSTANCE_MAP.get(cacheInstanceId);
    }


    /**
     * 将本地缓存实例存放在Map中方便管理
     *
     * @param cacheInstanceId
     * @param cache
     */
    public void setCache(String cacheInstanceId, Cache cache) {
        if(StringUtils.isEmpty(cacheInstanceId)){
            log.warn("缓存实例的ID不能为空 [自动补全ID]");
            cacheInstanceId= RandomUtil.getUUID();
        }
        CACHE_INSTANCE_MAP.compute(cacheInstanceId,(k,v)->{
            if(v!=null){
                throw new RuntimeException("缓存实例已经存在，请不要重复加载，cacheInstanceId="+v.getInstanceId());
            }
            return cache;
        });
    }

    /**
     * 根据接收到的消息的ID，判断是否需要处理这个消息
     * @param messageId
     * @return    是返回true 不是返回false
     */
    public boolean isMessageSendByMe(String messageId){
        if(StringUtils.isEmpty(messageId)){
            throw new RuntimeException(String.format("[CacheSupport][isMessageSendByMe]:消息ID不应该为空，messageId=%s",messageId));
        }
        return MESSAGE_MAP.size()!=0&&MESSAGE_MAP.get(messageId)!=null;
    }

    public void setCacheSyncMessageRecord(String messageId,CacheMessage message){
        MESSAGE_MAP.put(messageId,message);
    }


}
