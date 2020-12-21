package top.easyblog.seckill.server.controller;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import top.easyblog.seckill.api.error.AppResponseCode;
import top.easyblog.seckill.api.error.BusinessException;
import top.easyblog.seckill.api.response.CommonResponse;
import top.easyblog.seckill.api.service.OrderService;
import top.easyblog.seckill.model.UserModel;
import top.easyblog.seckill.server.service.RedisService;
import top.easyblog.seckill.server.service.TransactionOrderMQProducer;

import javax.servlet.http.HttpServletRequest;

/**
 @author Huang xin
 */
@Slf4j
@Controller("order")
@RequestMapping("/order")
@CrossOrigin(origins = {"*"},allowCredentials = "true")
public class OrderController extends BaseController {
    @Autowired
    private OrderService orderService;

    @Autowired
    private HttpServletRequest httpServletRequest;
    
    @Autowired
    private RedisService redisService;

    @Autowired
    private TransactionOrderMQProducer orderMQProducer;



    /**
     * 下单接口
     *
     * @param itemId
     * @param amount
     * @param promoId
     * @return
     * @throws BusinessException
     */
    @RequestMapping(value = "/create", method = {RequestMethod.POST}, consumes = {CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonResponse createOrder(@RequestParam(name="itemId")Integer itemId,
                                      @RequestParam(name="amount")Integer amount,
                                      @RequestParam(name="promoId",required = false)Integer promoId,
                                      @RequestParam(name = "token")String token) {
        //获取用户的登陆信息
        UserModel userModel = JSON.parseObject((String) redisService.get(token, RedisService.RedisDataBaseSelector.DB_0), UserModel.class);
        //这里可以更加复杂的用户身份验证，这里就简单验证一下用户是否登录
        if (userModel == null) {
            return CommonResponse.create(AppResponseCode.USER_NOT_LOGIN, "您还未登录，无法下单");
        }

        try{
            //不用直接调用篡改建订单的接口，可以异步创建订单
            //OrderModel orderModel = orderService.createOrder(userModel.getId(),itemId,promoId,amount);
            orderMQProducer.asyncSendDecrStockMsgInTransaction(itemId,amount,promoId,userModel.getId());
            return CommonResponse.create(AppResponseCode.SUCCESS,"下单成功");
        }catch (BusinessException e){
            log.error(e.getMessage());
            return CommonResponse.create(AppResponseCode.FAIL,e.getMessage());
        }
    }
}
