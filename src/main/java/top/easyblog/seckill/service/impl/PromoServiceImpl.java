package top.easyblog.seckill.service.impl;

import com.alibaba.fastjson.JSON;
import org.joda.time.DateTime;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.easyblog.seckill.CommonResponse;
import top.easyblog.seckill.cache.utils.RandomUtil;
import top.easyblog.seckill.conf.redis.RedisKeyManager;
import top.easyblog.seckill.error.AppResponseCode;
import top.easyblog.seckill.model.ItemModel;
import top.easyblog.seckill.model.PromoModel;
import top.easyblog.seckill.model.UserModel;
import top.easyblog.seckill.model.entity.PromoDO;
import top.easyblog.seckill.model.mapper.PromoDOMapper;
import top.easyblog.seckill.service.ItemService;
import top.easyblog.seckill.service.PromoService;
import top.easyblog.seckill.service.RedisService;
import top.easyblog.seckill.service.UserService;

import java.math.BigDecimal;
import java.util.List;

/**
 *
 * @author huangXin
 */
@Service
public class PromoServiceImpl implements PromoService {

    @Autowired
    private PromoDOMapper promoDOMapper;

    @Autowired
    private ItemService itemService;

    @Autowired
    private RedisService redisService;

    @Autowired
    private UserService userService;


    @Override
    public PromoModel getPromoByItemId(Integer itemId) {
        //获取对应商品的秒杀活动信息
        PromoDO promoDO = promoDOMapper.selectByItemId(itemId);
        return transformEntityToModel(promoDO);
    }


    @Override
    public void publishPromo(Integer id){
        //通过活动id获取活动
        PromoDO promoDO = promoDOMapper.selectByPrimaryKey(id);
        if(promoDO==null||promoDO.getItemId()==0){
            return;
        }
        ItemModel itemModel = itemService.getItemById(promoDO.getItemId());
        //发布活动之后将库存信息缓存到redis中
        redisService.set(RedisKeyManager.PROMO_ITEM_STOCK_PREFIX + itemModel.getId(), itemModel.getStock(), RedisService.RedisDataBaseSelector.DB_0);
    }

    @Override
    public void publishAllPromo(){
        List<PromoDO> promos = promoDOMapper.selectAll();
        if(promos==null||promos.size()==0){
            return;
        }
        promos.forEach(promoDO -> {
            ItemModel itemModel = itemService.getItemById(promoDO.getItemId());
            //发布活动之后将库存信息缓存到redis中,持久存储
            redisService.set(RedisKeyManager.PROMO_ITEM_STOCK_PREFIX + itemModel.getId(), itemModel.getStock(), RedisService.RedisDataBaseSelector.DB_0);
            //将大闸的限制数字设到redis内,持久存储
            redisService.set(RedisKeyManager.KILL_DOOR_PREFIX + promoDO.getId(), itemModel.getStock() * 5, RedisService.RedisDataBaseSelector.DB_0);
            //将秒杀商品缓存到redis中,持久存储
            redisService.set(RedisKeyManager.ITEM_KEY_PREFIX + itemModel.getId(), JSON.toJSONString(itemModel), RedisService.RedisDataBaseSelector.DB_0);
            if(itemModel.getStock()==0) {
               redisService.set(RedisKeyManager.PROMO_STOCK_INVALID_PREFIX + promoDO.getItemId(),true,RedisService.RedisDataBaseSelector.DB_0);
            }else{
                redisService.delete(RedisService.RedisDataBaseSelector.DB_0, RedisKeyManager.PROMO_STOCK_INVALID_PREFIX + promoDO.getItemId());
            }
        });
    }


    /**
     * 从缓存中获取秒杀商品数据
     *
     * @param promoId
     * @return
     */
    @Override
    public PromoModel getPromoModelInCache(Integer promoId) {
        if (promoId == null || promoId <= 0) {
            return null;
        }
        PromoModel promoModel = null;
        String jsonStr = (String) redisService.get( RedisKeyManager.ITEM_KEY_PREFIX + promoId, RedisService.RedisDataBaseSelector.DB_0);
        ItemModel itemModel = JSON.parseObject(jsonStr, ItemModel.class);
        promoModel = itemModel.getPromoModel();
        if (promoModel == null) {
            promoModel = this.transformEntityToModel(promoDOMapper.selectByPrimaryKey(promoId));
            redisService.set( RedisKeyManager.ITEM_KEY_PREFIX + itemModel.getId(), JSON.toJSONString(itemModel), RedisService.RedisDataBaseSelector.DB_0);
        }
        return promoModel;
    }

    /**
     * 检查业务交易是否正确
     *
     * @param promoId
     * @param itemId
     * @param userId
     * @return
     */
    @Override
    public CommonResponse checkTransaction(Integer promoId, Integer itemId, Integer userId) {
        //判断是否库存已售罄，若对应的售罄key存在，表示对应的商品已经售卖完了，直接返回下单失败
        if (redisService.exists(RedisKeyManager.PROMO_ITEM_STOCK_PREFIX + itemId, RedisService.RedisDataBaseSelector.DB_0)) {
            return CommonResponse.create(AppResponseCode.GOODS_STOCK_NOT_ENOUGH, "手慢一步，宝贝都被别人抢光啦！");
        }
        //获取活动秒杀商品详情
        PromoModel promoModel = getPromoModelInCache(promoId);

        //判断活动是否正在进行
        if (promoModel == null || promoModel.getStatus() != 2) {
            return CommonResponse.create(AppResponseCode.GOODS_STOCK_NOT_ENOUGH, "手慢一步，宝贝都被别人抢光啦！");
        }
        //判断item信息是否存在
        ItemModel itemModel = itemService.getItemByIdInCache(itemId);
        if (itemModel == null) {
            return CommonResponse.create(AppResponseCode.GOODS_STOCK_NOT_ENOUGH, "手慢一步，宝贝都被别人抢光啦！");
        }
        //判断用户信息是否存在
        UserModel userModel = userService.getUserByIdInCache(userId);
        if (userModel == null) {
            return CommonResponse.create(AppResponseCode.USER_NOT_LOGIN, "您还未登录，请登录后再来下单");
        }

        return CommonResponse.create(AppResponseCode.SUCCESS,"业务状态良好");
    }

    /**
     * 在生成交易token之前，将一个和秒杀业务逻辑无忧太大关系的业务剥离出来提前做，具体就是用户状态检查，秒杀详情商品库存的检查，等等
     * 如果检查都没问题那就返回交易token，并且在redis中记录这个token，并且给这个token设置一个3分钟的有效期，当用户下单的时候首先需要检验交易token
     * 如果交易token验证不通过，一律返回。这样就可以方式恶意用户使用脚本来刷我们的订单接口
     *
     * @param promoId
     * @param itemId
     * @param userId
     * @return
     */
    @Override
    public CommonResponse generateKillToken(Integer promoId, Integer itemId, Integer userId) {
        String killToken = RandomUtil.getUUID();
        //获取秒杀大闸的count数量，并且每次在获取一个token之后减1
        long result = redisService.decr(RedisKeyManager.KILL_DOOR_PREFIX + promoId, 1, RedisService.RedisDataBaseSelector.DB_0);
        if (result < 0) {
            return CommonResponse.create(AppResponseCode.USER_NOT_LOGIN, "手慢一步，宝贝都被别人抢光啦！");
        }
        //将生成的token存放到redis，并设置一个5分钟的有效期
        redisService.set(String.format(RedisKeyManager.KILL_TOKEN, promoId, userId, itemId), killToken, 180, RedisService.RedisDataBaseSelector.DB_0);
        return CommonResponse.create(AppResponseCode.SUCCESS, killToken);
    }

    private PromoModel transformEntityToModel(PromoDO promoDO) {
        //entity->model
        PromoModel promoModel = convertFromDataObject(promoDO);
        if (promoModel == null) {
            return null;
        }

        //判断当前时间是否秒杀活动即将开始或正在进行
        if (promoModel.getStartDate().isAfterNow()) {
            promoModel.setStatus(1);
        } else if (promoModel.getEndDate().isBeforeNow()) {
            promoModel.setStatus(3);
        } else {
            promoModel.setStatus(2);
        }
        return promoModel;
    }


    private PromoModel convertFromDataObject(PromoDO promoDO) {
        if(promoDO == null){
            return null;
        }
        PromoModel promoModel = new PromoModel();
        BeanUtils.copyProperties(promoDO,promoModel);
        promoModel.setPromoItemPrice(new BigDecimal(promoDO.getPromoItemPrice()));
        promoModel.setStartDate(new DateTime(promoDO.getStartDate()));
        promoModel.setEndDate(new DateTime(promoDO.getEndDate()));
        return promoModel;
    }
}
