package top.easyblog.seckill.cache.sync;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import top.easyblog.seckill.cache.CacheSupport;
import top.easyblog.seckill.cache.CacheSyncPolicy;
import top.easyblog.seckill.error.AppResponseCode;
import top.easyblog.seckill.error.BusinessException;

import java.nio.charset.StandardCharsets;

/**
 * 基于RocketMQ的广播消息实现的JVM本地内存分布式环境下的同步策略
 * @author ：Huang Xin
 * @since ：2020/12/18 13:14
 */
@Slf4j
@Component
public class RocketMQLocalCacheSyncPolicy implements CacheSyncPolicy {

    @Autowired
    private RocketMQTemplate rocketmqtemplate;


    @Value(value = "${mq.local-cache.topic}")
    private String localCacheTopic;

    @Autowired
    private CacheSupport cacheSupport;

    @Override
    public SendResult publish(CacheMessage msg) {
        String jsonStr = JSON.toJSON(msg).toString();
        //缓存变更通知消息： 主题 kill-local-cache-toppic tag: 消息ID  消息体： CacheMessage的json串
        Message message = new Message(localCacheTopic,msg.getInstanceId(),jsonStr.getBytes(StandardCharsets.UTF_8));
        DefaultMQProducer producer = rocketmqtemplate.getProducer();
        try{
            SendResult sendResult = producer.send(message);
            if (sendResult != null && sendResult.getSendStatus() == SendStatus.SEND_OK) {
                log.info("缓存更新通知消息发送成功,msg-id:{}", msg.getInstanceId());
            } else {
                //3次都失败了抛出异常
                throw new BusinessException(AppResponseCode.MQ_SEND_MESSAGE_FAIL);
            }
            msg.setStatus(CacheMessage.CACHE_MESSAGE_NOT_CONSUME);
            cacheSupport.setCacheSyncMessageRecord(msg.getInstanceId(),msg);
            return sendResult;
        }catch (Exception e){
            //消息发送失败
            msg.setStatus(CacheMessage.CACHE_MESSAGE_EXCEPTION);
            cacheSupport.setCacheSyncMessageRecord(msg.getInstanceId(),msg);
            log.info("缓存更新通知消息发送失败,msg-id:{},case:{}", msg.getInstanceId(),e.getMessage());
        }
        return null;
    }

}
