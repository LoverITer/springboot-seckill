package top.easyblog.seckill.cache.sync;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.annotation.SelectorType;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import top.easyblog.seckill.cache.Cache;
import top.easyblog.seckill.cache.CacheSupport;
import top.easyblog.seckill.cache.Level1Cache;
import top.easyblog.seckill.cache.consts.CacheOpt;

/**
 * 缓存消息监听器
 *
 * @author chenck
 * @date 2020/7/7 15:11
 */
@Slf4j
@Component
@RocketMQMessageListener(
        consumerGroup = "${mq.local-cache.consumer.group}",
        topic = "${mq.local-cache.topic}",
        //广播模式消费，这样一来每个客户端都可以收到缓存更新的消息了
        messageModel = MessageModel.BROADCASTING,
        selectorType = SelectorType.TAG,
        selectorExpression = "*")
public class CacheMessageListener implements RocketMQListener<MessageExt> {


    @Autowired
    private CacheSupport cacheSupport;

    @Override
    public void onMessage(MessageExt msg) {
        //解析出消息信息
        CacheMessage message = JSON.parseObject(new String(msg.getBody()), CacheMessage.class);
        if (message == null) {
            log.warn("收到空的缓存更新消息【丢弃】");
            return;
        }
        //由于是广播消息，所以这个消息发送者也会收到消息，可是它又不需要更新自己的内存了，所以这里入到这种情况直接返回
        if (!cacheSupport.isMessageSendByMe(message.getInstanceId())) {
            try {
                //其他节点收到更新缓存的消息
                log.info("接收到缓存同步的消息【开始同步】, msg-id={}, cacheName={},  optType={}, key={}",
                        message.getInstanceId(), message.getCacheName(), message.getOptType(), message.getKey());

                Level1Cache level1Cache = getLevel1Cache(message.getCacheName());
                if (null == level1Cache) {
                    return;
                }
                if (CacheOpt.CACHE_REFRESH.equals(message.getOptType())) {
                    //更新缓存
                    level1Cache.refresh(message.getKey(),message.getValue());
                    log.debug("本地缓存缓存更新成功");
                } else {
                    level1Cache.clearLocalCache(message.getKey());
                    log.debug("本地缓存缓存清理成功");
                }
            } catch (Exception e) {
                log.error("处理消息发生异常, msg-id={},cause={}", message.getInstanceId(), e);
            }
        } else {
            log.debug("收到自己的缓存更新广播消息: msg-id={},cacheKey={}【丢弃】", message.getInstanceId(), message.getKey());
        }
    }

    /**
     * 获取 Level1Cache
     */
    private Level1Cache getLevel1Cache(String cacheInstanceId) {
        Cache cache = cacheSupport.getCache(cacheInstanceId);
        if (null == cache) {
            log.warn("cache mush not exists or not instance. cacheInstanceId={}", cacheInstanceId);
            return null;
        }
        if (!(cache instanceof Level1Cache)) {
            log.warn("cache must be implements Level1Cache, class ={}", cache.getClass().getName());
            return null;
        }
        return (Level1Cache) cache;
    }
}
