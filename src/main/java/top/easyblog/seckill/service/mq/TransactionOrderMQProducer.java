package top.easyblog.seckill.service.mq;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.apache.rocketmq.spring.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.stereotype.Component;
import top.easyblog.seckill.cache.utils.RandomUtil;
import top.easyblog.seckill.error.AppResponseCode;
import top.easyblog.seckill.error.BusinessException;
import top.easyblog.seckill.model.dto.OrderDto;
import top.easyblog.seckill.model.entity.StockLogDO;
import top.easyblog.seckill.model.mapper.StockLogDOMapper;
import top.easyblog.seckill.service.OrderService;

/**
 * @author Huang Xin
 * @Description 使用事务型消息，保证异步扣减库存可以在订单事务完成之后进行
 * @data 2020/12/21 17:36
 */
@Slf4j
@Component
@RocketMQTransactionListener(txProducerGroup = "kill-async-producer-group")
public class TransactionOrderMQProducer implements RocketMQLocalTransactionListener {


    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    @Value(value = "${mq.async-stock.topic}")
    private String decrStockTopic;

    @Autowired
    private OrderService orderService;

    @Autowired
    private StockLogDOMapper stockLogDOMapper;

    /**
     * 事务型异步发送异步扣减数据库库存的消息
     *
     * @param orderDto 订单参数
     * @return
     */
    public void sendAsyncDecrStockMsgInTransaction(OrderDto orderDto) {
        if (orderDto == null) {
            throw new RuntimeException("param `orderDto` can not be null");
        }
        try {
            Message<String> msg = new GenericMessage<>(JSON.toJSON(orderDto).toString());
            //发送半消息：异步扣减库存，在本地事务成功提交之前此消息对消费者不可见
            TransactionSendResult result = rocketMQTemplate.sendMessageInTransaction("kill-async-producer-group", decrStockTopic + ":" + RandomUtil.getUUID(), msg, orderDto);
            if (result.getLocalTransactionState() == LocalTransactionState.COMMIT_MESSAGE) {
                log.info("发送异步扣减库存消息成功.orderInfo:{}", orderDto);
            } else {
                throw new BusinessException(AppResponseCode.MQ_SEND_MESSAGE_FAIL);
            }
        } catch (Exception e) {
            log.error("发送扣减数据库库存消息异常，orderInfo:{},case:{}", orderDto, e.getMessage());
        }
    }


    /**
     * 检查本地事务，此方法会被MQ自动调用
     *
     * @param message
     * @param args
     * @return COMMIT_MESSAGE   本地事务执行成功，提交消息让消费者消费
     * ROLLBACK_MESSAGE 本地事务执行失败，回滚消息（相当于没有发送消息）
     * UNKNOW            未知状态，还需要检查
     */
    @Override
    public RocketMQLocalTransactionState executeLocalTransaction(Message message, Object args) {
        OrderDto orderDto = (OrderDto) args;
        //处理真正的业务  创建订单
        log.info("回调本地事务，开始创建订单，stockLogId={}", orderDto.getStockLogId());
        try {
            //这里首先会预减库存
            orderService.createOrder(orderDto.getOrderId(), orderDto.getUserId(), orderDto.getItemId(), orderDto.getPromoId(), orderDto.getAmount(), orderDto.getStockLogId());
            log.info("创建订单成功，stockLogId={}", orderDto.getStockLogId());
        } catch (BusinessException e) {
            //本地数据库事务失败，回滚消息
            log.error("创建订单失败，即将回滚消息，case:{}", e.getMessage());
            //设置对应的订单流水状态为回滚状态
            StockLogDO stockLogDO = new StockLogDO();
            stockLogDO.setStockLogId(orderDto.getStockLogId());
            stockLogDO.setStatus(3);
            stockLogDOMapper.updateByPrimaryKeySelective(stockLogDO);
            return RocketMQLocalTransactionState.ROLLBACK;
        }
        return RocketMQLocalTransactionState.COMMIT;
    }

    @Override
    public RocketMQLocalTransactionState checkLocalTransaction(Message message) {
        OrderDto orderDto = JSON.parseObject((String) message.getPayload(), OrderDto.class);
        String stockLogId = orderDto.getStockLogId();
        log.info("检查订单是否创建成功");
        StockLogDO stockLogDO = stockLogDOMapper.selectByPrimaryKey(stockLogId);
        if (stockLogDO == null) {
            return RocketMQLocalTransactionState.UNKNOWN;
        }
        if (stockLogDO.getStatus() == 2) {
            return RocketMQLocalTransactionState.COMMIT;
        } else if (stockLogDO.getStatus() == 1) {
            return RocketMQLocalTransactionState.UNKNOWN;
        }
        return RocketMQLocalTransactionState.ROLLBACK;
    }
}
