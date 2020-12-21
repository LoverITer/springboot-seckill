package top.easyblog.seckill.server.service.impl;

import org.joda.time.DateTime;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.easyblog.seckill.api.service.ItemService;
import top.easyblog.seckill.api.service.PromoService;
import top.easyblog.seckill.model.ItemModel;
import top.easyblog.seckill.model.PromoModel;
import top.easyblog.seckill.model.entity.PromoDO;
import top.easyblog.seckill.model.mapper.PromoDOMapper;
import top.easyblog.seckill.server.service.RedisService;

import java.math.BigDecimal;
import java.util.List;

/**
 * Created by hzllb on 2018/11/18.
 */
@Service
public class PromoServiceImpl implements PromoService {

    @Autowired
    private PromoDOMapper promoDOMapper;

    @Autowired
    private ItemService itemService;

    @Autowired
    private RedisService redisService;

    /**
     * redis商品库存key
     */
    public static final String PROMO_ITEM_STOCK_PREFIX="promo_item_stock_";

    /**
     * 商品库存map 的 key
     */
    public static final String PROMO_ITEM_MAP="promo_item_map";

    @Override
    public PromoModel getPromoByItemId(Integer itemId) {
        //获取对应商品的秒杀活动信息
        PromoDO promoDO = promoDOMapper.selectByItemId(itemId);

        //top.easyblog.seckill.model.entity->model
        PromoModel promoModel = convertFromDataObject(promoDO);
        if(promoModel == null){
            return null;
        }

        //判断当前时间是否秒杀活动即将开始或正在进行
        if(promoModel.getStartDate().isAfterNow()){
            promoModel.setStatus(1);
        }else if(promoModel.getEndDate().isBeforeNow()){
            promoModel.setStatus(3);
        }else{
            promoModel.setStatus(2);
        }
        return promoModel;
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
        redisService.hset(PROMO_ITEM_MAP,PROMO_ITEM_STOCK_PREFIX+itemModel.getId(),itemModel.getStock(), RedisService.RedisDataBaseSelector.DB_0);
    }


    public void publishAllPromo(){
        List<PromoDO> promos = promoDOMapper.selectAll();
        if(promos==null||promos.size()==0){
            return;
        }
        promos.forEach(promoDO -> {
            ItemModel itemModel = itemService.getItemById(promoDO.getItemId());
            //发布活动之后将库存信息缓存到redis中
            redisService.hset(PROMO_ITEM_MAP,PROMO_ITEM_STOCK_PREFIX+itemModel.getId(),itemModel.getStock(), RedisService.RedisDataBaseSelector.DB_0);
        });
    }


   private PromoModel convertFromDataObject(PromoDO promoDO){
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
