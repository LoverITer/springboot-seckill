package top.easyblog.seckill.service.impl;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.easyblog.seckill.error.BusinessException;
import top.easyblog.seckill.error.EmBusinessError;
import top.easyblog.seckill.model.ItemModel;
import top.easyblog.seckill.model.OrderModel;
import top.easyblog.seckill.model.entity.OrderDO;
import top.easyblog.seckill.model.entity.StockLogDO;
import top.easyblog.seckill.model.mapper.OrderDOMapper;
import top.easyblog.seckill.model.mapper.StockLogDOMapper;
import top.easyblog.seckill.service.IDGenerationService;
import top.easyblog.seckill.service.ItemService;
import top.easyblog.seckill.service.OrderService;

import java.math.BigDecimal;

/**
 *
 * @author Huang Xin
 */
@Service
public class OrderServiceImpl implements OrderService {


    @Autowired
    private ItemService itemService;

    @Autowired
    private StockLogDOMapper stockLogMapper;

    @Autowired
    private OrderDOMapper orderDOMapper;

    @Autowired
    private IDGenerationService idGenerationService;



    /**
     * 秒杀下单核心业务逻辑
     *
     * @param userId
     * @param itemId
     * @param promoId
     * @param amount
     * @return
     * @throws BusinessException
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderModel createOrder(String orderId,Integer userId, Integer itemId, Integer promoId, Integer amount,String stockLogId) throws BusinessException {

        //校验下单状态,下单的商品是否存在，购买数量是否足够
        ItemModel itemModel = itemService.getItemByIdInCache(itemId);
        if (itemModel == null || itemModel.getStock() < 0) {
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "商品不存在或者未在秒杀时间段");
        }

        /*
         * 购买数量后置检查
         * 需要验证用户下单时所需购买量 库存是否满足
         */
        if (amount <= 0 || amount > 5) {
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "用户抢购物品数量不正确");
        }

        //落单减库存 ，只是预减库存
        boolean result = itemService.decreaseStock(itemId,amount);
        if(!result){
            throw new BusinessException(EmBusinessError.STOCK_NOT_ENOUGH);
        }

        //预订单入库
        OrderModel orderModel = new OrderModel();
        orderModel.setId(orderId);
        orderModel.setUserId(userId);
        orderModel.setItemId(itemId);
        orderModel.setAmount(amount);
        if(promoId != null){
            orderModel.setItemPrice(itemModel.getPromoModel().getPromoItemPrice());
        }else{
            orderModel.setItemPrice(itemModel.getPrice());
        }
        orderModel.setPromoId(promoId);
        orderModel.setOrderPrice(orderModel.getItemPrice().multiply(new BigDecimal(amount)));

        OrderDO orderDO = convertFromOrderModel(orderModel);
        orderDOMapper.insertSelective(orderDO);

        //将商品订单流水线状态修改为成功（即已下单）
        StockLogDO stockLogDO = new StockLogDO();
        stockLogDO.setStockLogId(stockLogId);
        stockLogDO.setStatus(2);
        stockLogMapper.updateByPrimaryKeySelective(stockLogDO);

        /*
        * 8. 返回订单给前端，后续交给支付模块处理
        * 这里为了防止用户超时为支付，需要使用MQ实现用户超时之后库存恢复的过程
         */

        return orderModel;
    }



    private OrderDO convertFromOrderModel(OrderModel orderModel){
        if(orderModel == null){
            return null;
        }
        OrderDO orderDO = new OrderDO();
        BeanUtils.copyProperties(orderModel,orderDO);
        orderDO.setItemPrice(orderModel.getItemPrice().doubleValue());
        orderDO.setOrderPrice(orderModel.getOrderPrice().doubleValue());
        return orderDO;
    }
}
