package top.easyblog.seckill.service;

import top.easyblog.seckill.CommonResponse;
import top.easyblog.seckill.model.PromoModel;

/**
 * Created by hzllb on 2018/11/18.
 */
public interface PromoService {
    //根据itemid获取即将进行的或正在进行的秒杀活动
    PromoModel getPromoByItemId(Integer itemId);


    void publishPromo(Integer id);

    void publishAllPromo();

    /**
     * 生成秒杀令牌
     *
     * @param promoId
     * @return
     */
    CommonResponse generateKillToken(Integer promoId, Integer itemId, Integer userId);

    CommonResponse checkTransaction(Integer promoId, Integer itemId, Integer userId);

    PromoModel getPromoModelInCache(Integer promoId);

}
