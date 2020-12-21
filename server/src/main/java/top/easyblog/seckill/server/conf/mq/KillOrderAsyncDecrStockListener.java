package top.easyblog.seckill.server.conf.mq;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.annotation.SelectorType;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import top.easyblog.seckill.api.service.ItemService;
import top.easyblog.seckill.model.mapper.ItemStockDOMapper;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 秒杀订单异步扣减库存
 *
 * @author ：huangxin
 * @modified ：
 * @since ：2020/12/11 14:27
 */
@Slf4j
@Component
@RocketMQMessageListener(
        consumerGroup = "${mq.async-stock.consumer.group}",
        topic = "${mq.async-stock.topic}",
        selectorType = SelectorType.TAG,
        selectorExpression = "*")
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
            Map<String, Integer> orderInfo = JSON.parseObject(new String(msg.getBody(), StandardCharsets.UTF_8), Map.class);
            if (orderInfo == null) {
                return;
            }
            itemStockDOMapper.decreaseStock(orderInfo.get("itemId"), orderInfo.get("amount"));
            itemService.increaseSales(orderInfo.get("itemId"), orderInfo.get("amount"));
            log.info("异步扣减库存成功");
        } catch (Exception e) {
            e.printStackTrace();
            log.error("处理扣减库存失败，msg:{}", e.getMessage());
        }
    }

}
