package top.easyblog.seckill.service.mq;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import top.easyblog.seckill.cache.utils.RandomUtil;
import top.easyblog.seckill.model.dto.OrderDto;

import java.nio.charset.StandardCharsets;

/**
 * @author Huang Xin
 * @Description 创建订单消息服务
 * @data 2020/12/24 11:27
 */
@Slf4j
@Service
public class RocketMQKillOrderProducer {

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    @Value(value = "${mq.order-service.topic}")
    private String ordersTopic;


    /**
     * 给下游创建订单的服务发送消息，
     *
     * @return
     */
    public boolean sendCreateOrderMsg(OrderDto orderDto) {
        try {
            //发送创建订单的消息
            Message message = new Message(ordersTopic, RandomUtil.getUUID(),JSON.toJSONString(orderDto).getBytes(StandardCharsets.UTF_8));
            DefaultMQProducer producer = rocketMQTemplate.getProducer();
            SendResult sendResult = producer.send(message);
            if (sendResult.getSendStatus() == SendStatus.SEND_OK) {
                log.info("创建订单消息发送成功,orderInfo:{}", orderDto);
                return true;
            }
        } catch (Exception e) {
            log.error("创建订单消息发送失败,orderInfo:{},{}", orderDto, e.getMessage());
        }
        return false;
    }

}
