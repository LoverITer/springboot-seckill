package top.easyblog.seckill.server.service.impl;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.easyblog.seckill.api.error.BusinessException;
import top.easyblog.seckill.api.error.EmBusinessError;
import top.easyblog.seckill.api.service.ItemService;
import top.easyblog.seckill.api.service.OrderService;
import top.easyblog.seckill.api.service.UserService;
import top.easyblog.seckill.model.ItemModel;
import top.easyblog.seckill.model.OrderModel;
import top.easyblog.seckill.model.entity.OrderDO;
import top.easyblog.seckill.model.mapper.OrderDOMapper;
import top.easyblog.seckill.server.service.IDGenerationService;
import top.easyblog.seckill.server.service.RocketMQProducerService;

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
    private UserService userService;

    @Autowired
    private OrderDOMapper orderDOMapper;

    @Autowired
    private IDGenerationService idGenerationService;

    @Autowired
    private RocketMQProducerService service;

    /**
     * 用户对同一件商品购买数量的限制
     */
    private static final Integer USER_PURCHASE_AMOUNT_IN_ONE_ITEM_LIMIT = 2;


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
    public OrderModel createOrder(Integer userId, Integer itemId, Integer promoId, Integer amount) throws BusinessException {

        /*1. 购买数量前置检查
         * 首先需要校验购买的数量是大于0，不能产生一个空的或无效的订单
         * 其次，用户可能多次下单，因此需要限制一个用户对同一个产品无论下单多少次，都只能购买2件
         * 在者需要验证用户下单时所需购买量 库存是否满足
         */
        Integer purchaseAmount = orderDOMapper.selectUserPurchaseAmount(userId, itemId);
        /*if (amount <= 0 || purchaseAmount >= USER_PURCHASE_AMOUNT_IN_ONE_ITEM_LIMIT ||
                amount + purchaseAmount > USER_PURCHASE_AMOUNT_IN_ONE_ITEM_LIMIT) {
            throw new BusinessException(EmBusinessError.ITEM_USER_KILLED, "同一件商品购买数量不能超过两件");
        }*/

        //2.校验下单状态,下单的商品是否存在，购买数量是否足够
        ItemModel itemModel = itemService.getItemByIdInCache(itemId);
        if (itemModel == null || itemModel.getStock() < 0) {
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "商品不存在或者未在秒杀时间段");
        }

        /*
         * 3. 购买数量后置检查
         * 需要验证用户下单时所需购买量 库存是否满足
         */
        if (itemModel.getStock() - amount < 0) {
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "用户抢购物品数量不正确");
        }

        /*
         *4. 校验活动信息
         * 主要是检查用户下单时当前商品是否处于秒杀活动状态，通过一个标志位实现
         */
        if(promoId != null){
            if (itemModel.getPromoModel().getStatus() != 2) {
                //校验活动是否正在进行中
                throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "秒杀活动还未开始，请稍等！");
            }
        }

        //5. 落单减库存 ，只是预减库存
        boolean result = itemService.decreaseStock(itemId,amount);
        if(!result){
            throw new BusinessException(EmBusinessError.STOCK_NOT_ENOUGH);
        }

        //6. 预订单入库
        OrderModel orderModel = new OrderModel();
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

        //使用雪花算法生成交易流水号,订单号
        orderModel.setId(String.valueOf(idGenerationService.nextId()));
        OrderDO orderDO = convertFromOrderModel(orderModel);
        orderDOMapper.insertSelective(orderDO);

        //7. 增加商品的销量
        itemService.increaseSales(itemId,amount);

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
