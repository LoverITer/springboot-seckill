package top.easyblog.seckill.server.service;

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
import top.easyblog.seckill.api.error.AppResponseCode;
import top.easyblog.seckill.api.error.BusinessException;
import top.easyblog.seckill.model.OrderModel;
import top.easyblog.seckill.model.mapper.OrderDOMapper;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * @author ：huangxin
 * @since ：2020/12/11 15:12
 */
@Slf4j
@Service
public class RocketMQProducerService {

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    @Autowired
    private OrderDOMapper orderDOMapper;

    @Value(value = "${mq.email.topic}")
    private String emailTopic;

    @Value(value = "${mq.timeout-order.topic}")
    private String orderTopic;


    /**
     * 秒杀成功异步发送邮件通知消息
     */
    public boolean sendKillSuccessMsg(OrderModel order) throws BusinessException {
        if (Objects.isNull(order)) {
            throw new BusinessException(AppResponseCode.ORDER_INVALID);
        }
        log.info("秒杀成功[订单ID:{}] 异步发送邮件通知消息-准备发送消息", order.getId());
        //将订单消息发送到消息队列中
        DefaultMQProducer producer = rocketMQTemplate.getProducer();
        Message emailMsg = new Message(emailTopic, order.getId(), JSON.toJSON(order).toString().getBytes(StandardCharsets.UTF_8));
        try {
            SendResult sendResult = producer.send(emailMsg);;
            if (sendResult != null && sendResult.getSendStatus() == SendStatus.SEND_OK) {
                log.info("秒杀成功[订单ID:{}] 异步发送邮件通知消息-消息发送成功", order.getId());
                return true;
            } else {
                //3次都失败了抛出异常
                throw new BusinessException(AppResponseCode.MQ_SEND_MESSAGE_FAIL);
            }
        } catch (Exception e) {
            e.printStackTrace();
            log.error("秒杀成功[订单ID:{}] 异步发送邮件通知消息-发生异常，错误为：{}", order.getId(), e.getMessage());
            return false;
        }

    }

    /**
     * 秒杀成功后生成抢购订单-发送延时消息等待着1小时，如果订单任然没有支付。那就将未支付的订单失效
     * @return
     */
    public boolean sendKillSuccessMonitorOrderTimOutMsg(OrderModel order) throws BusinessException{
        if (Objects.isNull(order)) {
            throw new BusinessException(AppResponseCode.ORDER_INVALID);
        }
        log.info("秒杀成功[订单ID:{}] 发送订单超时监控消息-准备发送消息", order.getId());
        //将订单消息发送到消息队列中
        DefaultMQProducer producer = rocketMQTemplate.getProducer();

        Message orderMsg = new Message(orderTopic, order.getId(), JSON.toJSON(order).toString().getBytes(StandardCharsets.UTF_8));
        // 设置延时等级RocketMQ,这个消息将在30s之后发送(RocketMQ现在只支持固定的几个时间,详看delayTimeLevel)
        //"1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m 1h(17) 2h";
        orderMsg.setDelayTimeLevel(3);
        try {
            SendResult sendResult = producer.send(orderMsg);;
            if (sendResult != null && sendResult.getSendStatus() == SendStatus.SEND_OK) {
                log.info("秒杀成功[订单ID:{}]发送订单超时监控消息-消息发送成功", order.getId());
                return true;
            } else {
                //没有超过就抛出异常重试
                throw new BusinessException(AppResponseCode.MQ_SEND_MESSAGE_FAIL);
            }
        } catch (Exception e) {
            e.printStackTrace();
            log.error("秒杀成功[订单ID:{}] 发送订单超时监控消息-发生异常，准备重试，错误为：{}", order.getId(), e.getMessage());
            return false;
        }
    }

}
