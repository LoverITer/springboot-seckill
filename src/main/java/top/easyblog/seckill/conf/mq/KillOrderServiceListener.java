package top.easyblog.seckill.conf.mq;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQPushConsumerLifecycleListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import top.easyblog.seckill.model.dto.OrderDto;
import top.easyblog.seckill.service.ItemService;
import top.easyblog.seckill.service.mq.TransactionOrderMQProducer;

import java.nio.charset.StandardCharsets;

/**
 * @author Huang Xin
 * @Description 秒杀下单服务，处理下单业务，由于下单的订单必须要和数据库，所以考虑使用MQ来削峰填谷，让系统按照自己的能力去消费
 * @data 2020/12/23 23:53
 */
@Slf4j
@Component
@RocketMQMessageListener(
        consumerGroup = "${mq.order-service.consumer.group}",
        topic = "${mq.order-service.topic}",
        messageModel = MessageModel.CLUSTERING)
public class KillOrderServiceListener implements RocketMQListener<MessageExt>, RocketMQPushConsumerLifecycleListener{

    @Autowired
    private ItemService itemService;

    /**
     * 事务型下单操作
     */
    @Autowired
    private TransactionOrderMQProducer transactionOrderMQProducer;

    @Override
    public void onMessage(MessageExt msg) {
        log.debug("收到创建订单的消息");
        //接收订单消息并解析处理
        OrderDto orderDto = JSON.parseObject(new String(msg.getBody(), StandardCharsets.UTF_8), OrderDto.class);
        if (orderDto == null) {
            return;
        }

        log.debug("开始初始化订单流水状态,stockLogId={}", orderDto.getStockLogId());
        //初始化一个订单库存流水为init状态，关键就是里面的一个status字段为1表示初始 2 表示是订单成功 3 表示订单无效
        String stockLogId = itemService.initStockLog(orderDto);
        orderDto.setStockLogId(stockLogId);
        log.debug("订单流水初始化成功,stockLogId:{}", stockLogId);
        //完成流水任务之后，发送事务型消息通知下游服务完成订单创建和库存扣减（秒杀核心）
        transactionOrderMQProducer.sendAsyncDecrStockMsgInTransaction(orderDto);
    }

    @Override
    public void prepareStart(DefaultMQPushConsumer consumer) {
        // 每次拉取的间隔，单位为毫秒,5s处理2*32*8=512条消息
        consumer.setPullInterval(5000);
        // 设置每次从队列中拉取的消息数为32
        consumer.setPullBatchSize(32);
    }
}
