package top.easyblog.seckill.server.controller;

import com.alibaba.fastjson.JSON;
import top.easyblog.seckill.api.error.AppResponseCode;
import top.easyblog.seckill.api.response.CommonResponse;
import top.easyblog.seckill.api.service.OrderService;
import top.easyblog.seckill.api.error.BusinessException;
import top.easyblog.seckill.api.error.EmBusinessError;
import top.easyblog.seckill.server.service.RedisService;
import top.easyblog.seckill.model.OrderModel;
import top.easyblog.seckill.model.UserModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import top.easyblog.seckill.api.utils.CookieUtils;

import javax.servlet.http.HttpServletRequest;

/**
 * Created by hzllb on 2018/11/18.
 */
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

    //封装下单请求
    @RequestMapping(value = "/createorder",method = {RequestMethod.POST},consumes={CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonResponse<OrderModel> createOrder(@RequestParam(name="itemId")Integer itemId,
                                      @RequestParam(name="amount")Integer amount,
                                      @RequestParam(name="promoId",required = false)Integer promoId) throws BusinessException {

        //首先检查用户是否登录
        String userLoginToken = CookieUtils.getCookieValue(httpServletRequest, USER_SESSION_REDIS_KEY);
        //获取用户的登陆信息
        UserModel userModel = JSON.parseObject((String) redisService.get(userLoginToken, RedisService.RedisDataBaseSelector.DB_0),UserModel.class);
        if(userModel==null){
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN,"用户还未登陆，不能下单");
        }

        OrderModel orderModel = orderService.createOrder(userModel.getId(),itemId,promoId,amount);

        return CommonResponse.create(AppResponseCode.SUCCESS,orderModel);
    }
}
