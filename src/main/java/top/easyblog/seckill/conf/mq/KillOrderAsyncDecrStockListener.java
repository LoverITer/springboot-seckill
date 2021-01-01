package top.easyblog.seckill.conf.mq;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import top.easyblog.seckill.model.dto.OrderDto;
import top.easyblog.seckill.model.mapper.ItemStockDOMapper;
import top.easyblog.seckill.service.ItemService;

import java.nio.charset.StandardCharsets;

/**
 * 秒杀订单异步扣减库存并生成订单
 *
 * @author ：huangxin
 * @modified ：
 * @since ：2020/12/11 14:27
 */
@Slf4j
@Component
@RocketMQMessageListener(
        consumerGroup = "${mq.async-stock.consumer.group}",
        topic = "${mq.async-stock.topic}")
public class KillOrderAsyncDecrStockListener implements RocketMQListener<MessageExt> {


    @Autowired
    private ItemStockDOMapper itemStockDOMapper;

    @Autowired
    private ItemService itemService;



    /**
     * 实现库存到数据库的真正扣减
     *
     * @param msg
     */
    @Override
    public void onMessage(MessageExt msg) {
        try {
            log.info("收到异步扣减库存消息,{}", msg);
            //接收订单消息并解析处理
            OrderDto orderDto = JSON.parseObject(new String(msg.getBody(), StandardCharsets.UTF_8), OrderDto.class);
            if (orderDto == null) {
                return;
            }

            //减库存
            itemStockDOMapper.decreaseStock(orderDto.getItemId(), orderDto.getAmount());
            //增加销量
            itemService.increaseSales(orderDto.getItemId(), orderDto.getAmount());
            log.info("异步扣减库存成功");
            /* 扣减库存成功之后给用户发送抢购成功的消息
             * 防止用户超时为支付，需要使用MQ实现用户超时之后库存恢复的过程
             */
            //rocketMQProducerService.sendKillSuccessMsg(orderModel);
        } catch (Exception e) {
            e.printStackTrace();
            log.error("处理扣减库存失败，msg:{}", e.getMessage());
        }
    }


}
