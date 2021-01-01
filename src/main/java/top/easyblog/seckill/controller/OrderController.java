package top.easyblog.seckill.controller;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import top.easyblog.seckill.CommonResponse;
import top.easyblog.seckill.conf.redis.RedisKeyManager;
import top.easyblog.seckill.error.AppResponseCode;
import top.easyblog.seckill.error.BusinessException;
import top.easyblog.seckill.model.ItemModel;
import top.easyblog.seckill.model.PromoModel;
import top.easyblog.seckill.model.UserModel;
import top.easyblog.seckill.model.dto.OrderDto;
import top.easyblog.seckill.service.IDGenerationService;
import top.easyblog.seckill.service.ItemService;
import top.easyblog.seckill.service.PromoService;
import top.easyblog.seckill.service.RedisService;
import top.easyblog.seckill.service.mq.RocketMQKillOrderProducer;
import top.easyblog.seckill.utils.CodeUtil;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.util.Map;

/**
 @author Huang xin
 */
@Slf4j
@Controller("order")
@RequestMapping("/order")
@CrossOrigin(origins = {"*"},allowCredentials = "true")
public class OrderController extends BaseController {


    @Autowired
    private RedisService redisService;

    @Autowired
    private PromoService promoService;

    @Autowired
    private ItemService itemService;

    @Autowired
    private RocketMQKillOrderProducer orderProducer;

    @Autowired
    private IDGenerationService idGenerationService;


    @ResponseBody
    @RequestMapping(value = "/verify-code", method = {RequestMethod.GET})
    public CommonResponse<String> generateVerifyCode(HttpServletResponse response, @RequestParam(name = "token", required = false) String token) throws BusinessException, IOException {
        //检查用户登录状态
        UserModel userModel = JSON.parseObject((String) redisService.get(token, RedisService.RedisDataBaseSelector.DB_0), UserModel.class);
        if (userModel == null) {
            return CommonResponse.create(AppResponseCode.USER_NOT_LOGIN, "您还未登录，无法下单");
        }

        Map<String, Object> map = CodeUtil.generateCodeAndPic();
        //设置验证码5分钟的有效期
        redisService.set(RedisKeyManager.VERIFY_CODE_PREFIX + userModel.getId(), map.get("code"), 600, RedisService.RedisDataBaseSelector.DB_0);
        ImageIO.write((RenderedImage) map.get("codePic"), "jpeg", response.getOutputStream());
        return CommonResponse.create(AppResponseCode.SUCCESS);
    }

    /**
     * 生成秒杀令牌
     *
     * @param itemId  物品ID
     * @param promoId 活动秒杀商品ID
     * @param token   用户登录Token
     * @return
     */
    @RequestMapping(value = "/kill-token", method = {RequestMethod.POST}, consumes = {CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonResponse generateKillToken(@RequestParam(name = "itemId") Integer itemId,
                                            @RequestParam(name = "promoId") Integer promoId,
                                            @RequestParam(name = "token", required = false) String token,
                                            @RequestParam(name = "verifyCode") String verifyCode) {
        //获取用户的登陆信息
        UserModel userModel = JSON.parseObject((String) redisService.get(token, RedisService.RedisDataBaseSelector.DB_0), UserModel.class);
        //这里可以更加复杂的用户身份验证，这里就简单验证一下用户是否登录
        if (userModel == null) {
            return CommonResponse.create(AppResponseCode.USER_NOT_LOGIN, "您还未登录，无法下单");
        }

        //通过verifycode验证验证码的有效性
        String redisVerifyCode = (String) redisService.get(RedisKeyManager.VERIFY_CODE_PREFIX + userModel.getId(), RedisService.RedisDataBaseSelector.DB_0);
        if (StringUtils.isEmpty(redisVerifyCode)) {
            return CommonResponse.create(AppResponseCode.FAIL, "请求非法");
        }
        if (!redisVerifyCode.equalsIgnoreCase(verifyCode)) {
            return CommonResponse.create(AppResponseCode.FAIL, "验证码错误或验证码已过期");
        }

        //获取秒杀访问令牌
        return promoService.generateKillToken(promoId, itemId, userModel.getId());
    }


    /**
     * 秒杀核心接口
     *
     * @param itemId
     * @param amount
     * @param promoId
     * @param token
     * @param killToken
     * @return
     */
    @GetMapping(value = "/back_end/create")
    @ResponseBody
    public CommonResponse createOrder(@RequestParam(name="itemId")Integer itemId,
                                      @RequestParam(name="amount")Integer amount,
                                      @RequestParam(name="promoId",required = false)Integer promoId,
                                      @RequestParam(name = "token", required = false) String token,
                                      @RequestParam(name = "killToken", required = false) String killToken) {
        //获取用户的登陆信息
        UserModel userModel = JSON.parseObject((String) redisService.get(token, RedisService.RedisDataBaseSelector.DB_0), UserModel.class);
        //这里可以更加复杂的用户身份验证，这里就简单验证一下用户是否登录
        if (userModel == null) {
            return CommonResponse.create(AppResponseCode.USER_NOT_LOGIN, "您还未登录，无法下单");
        }

        //校验秒杀令牌是否正确
       /* if (promoId != null) {
            String inRedisKillToken = (String) redisService.get(String.format(RedisKeyManager.KILL_TOKEN,promoId,userModel.getId(),itemId), RedisService.RedisDataBaseSelector.DB_0);
            //验证Token是否是在前端页面生成的
            if (inRedisKillToken == null||!StringUtils.equals(killToken, inRedisKillToken)) {
                return CommonResponse.create(AppResponseCode.FAIL, "秒杀令牌校验失败");
            }
        }*/

        //判断是否库存已售罄，若对应的售罄key存在，表示对应的商品已经售卖完了，直接返回下单失败
        if (redisService.exists(RedisKeyManager.PROMO_STOCK_INVALID_PREFIX + itemId, RedisService.RedisDataBaseSelector.DB_0)) {
            return CommonResponse.create(AppResponseCode.GOODS_STOCK_NOT_ENOUGH, "手慢一步，宝贝都被别人抢光啦！");
        }
        //判断item信息是否存在
        ItemModel itemModel = itemService.getItemByIdInCache(itemId);
        if (itemModel == null) {
            return CommonResponse.create(AppResponseCode.GOODS_STOCK_NOT_ENOUGH, "手慢一步，宝贝都被别人抢光啦！");
        }

        //获取活动秒杀商品详情
        PromoModel promoModel = itemModel.getPromoModel();

        //判断活动是否正在进行
        if (promoModel == null || promoModel.getStatus() != 2) {
            return CommonResponse.create(AppResponseCode.GOODS_STOCK_NOT_ENOUGH, "该商品未在活动时间");
        }
        //使用雪花算法生成交易流水号,订单号
        long orderId = idGenerationService.nextId();
        //一切验证没有问题之后，将下单任务交给MQ异步来执行
        OrderDto orderDto = OrderDto.createOrderDto(String.valueOf(orderId),userModel.getId(), itemId, promoId, amount);
        if (orderProducer.sendCreateOrderMsg(orderDto)) {
            //将订单ID交给前端去轮询订单状态
            return CommonResponse.create(AppResponseCode.SUCCESS,orderId);
        }
        return CommonResponse.create(AppResponseCode.FAIL, "下单失败");
    }
}
