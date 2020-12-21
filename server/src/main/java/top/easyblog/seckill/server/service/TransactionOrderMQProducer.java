package top.easyblog.seckill.server.service;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.TransactionListener;
import org.apache.rocketmq.client.producer.TransactionMQProducer;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import top.easyblog.seckill.api.error.AppResponseCode;
import top.easyblog.seckill.api.error.BusinessException;
import top.easyblog.seckill.api.service.OrderService;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author      Huang Xin
 * @Description 使用事务型消息，保证异步扣减库存可以在订单事务完成之后进行
 * @data        2020/12/21 17:36
 */
@Slf4j
@Component
@RocketMQTransactionListener(txProducerGroup = "kill-async-producer-group")
public class TransactionOrderMQProducer implements TransactionListener {


    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    @Value(value = "${mq.async-stock.topic}")
    private String decrStockTopic;

    @Autowired
    private OrderService orderService;

    /**
     * 事务型异步发送扣减数据库库存的消息
     * @param itemId
     * @param amount
     * @return
     */
    public boolean asyncSendDecrStockMsgInTransaction(Integer itemId,Integer amount,Integer promoId,Integer userId)throws BusinessException{
        Map<String,Integer> orderInfo = new HashMap<>(16);
        orderInfo.put("itemId",itemId);
        orderInfo.put("amount",amount);

        Map<String,Integer> argsMap=new HashMap<>(16);
        argsMap.put("itemId",itemId);
        argsMap.put("amount",amount);
        argsMap.put("promoId",promoId);
        argsMap.put("userId",userId);
        try{
            Message message = new Message(decrStockTopic, JSON.toJSON(orderInfo).toString().getBytes(StandardCharsets.UTF_8));
            TransactionMQProducer producer = (TransactionMQProducer)rocketMQTemplate.getProducer();
            //发送事务消息：下单成功并且数据库事务提交之后异步扣减库存
            TransactionSendResult result = producer.sendMessageInTransaction(message, argsMap);
            if(result.getLocalTransactionState()== LocalTransactionState.COMMIT_MESSAGE){
                log.info("发送异步扣减库存消息成功.orderInfo:{}",orderInfo);
                return true;
            }else{
                throw new BusinessException(AppResponseCode.MQ_SEND_MESSAGE_FAIL);
            }
        }catch (Exception e){
            log.error("发送扣减数据库库存消息异常，orderInfo:{},case:{}", orderInfo, e.getMessage());
            return false;
        }
    }


    /**
     * 检查本地事务，如果下单这一串的操作都执行成功之后爱提交消息扣减库存
     *
     * @param message
     * @param args
     * @return COMMIT_MESSAGE   本地事务执行成功，提交消息让消费者消费
     *         ROLLBACK_MESSAGE 本地事务执行失败，回滚消息（相当于没有发送消息）
     *         UNKNOW            未知状态，还需要检查
     */
    @Override
    public LocalTransactionState executeLocalTransaction(Message message, Object args) {
        //处理真正的业务  创建订单
        Integer userId=(Integer)((Map)args).get("userId");
        Integer amount=(Integer)((Map)args).get("amount");
        Integer promoId=(Integer)((Map)args).get("promoId");
        Integer itemId=(Integer)((Map)args).get("itemId");
        try {
            orderService.createOrder(userId,itemId,promoId,amount);
        } catch (BusinessException e) {
            //本地数据库事务失败，回滚消息
            log.error("创建订单失败，即将回滚消息，case:{}",e.getMessage());
            return LocalTransactionState.ROLLBACK_MESSAGE;
        }
        return LocalTransactionState.COMMIT_MESSAGE;
    }

    @Override
    public LocalTransactionState checkLocalTransaction(MessageExt messageExt) {
        return null;
    }
}
