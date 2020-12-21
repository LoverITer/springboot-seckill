package top.easyblog.seckill.server.service.impl;

import com.alibaba.fastjson.JSON;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import top.easyblog.seckill.api.error.BusinessException;
import top.easyblog.seckill.api.error.EmBusinessError;
import top.easyblog.seckill.api.service.ItemService;
import top.easyblog.seckill.api.service.PromoService;
import top.easyblog.seckill.model.ItemModel;
import top.easyblog.seckill.model.PromoModel;
import top.easyblog.seckill.model.entity.ItemDO;
import top.easyblog.seckill.model.entity.ItemStockDO;
import top.easyblog.seckill.model.mapper.ItemDOMapper;
import top.easyblog.seckill.model.mapper.ItemStockDOMapper;
import top.easyblog.seckill.server.service.RedisService;
import top.easyblog.seckill.server.service.RocketMQProducerService;
import top.easyblog.seckill.server.validator.ValidationResult;
import top.easyblog.seckill.server.validator.ValidatorImpl;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by Huang Xin
 */
@Service
public class ItemServiceImpl implements ItemService {

    @Autowired
    private ValidatorImpl validator;

    @Autowired
    private ItemDOMapper itemDOMapper;

    @Autowired
    private PromoService promoService;

    @Autowired
    private ItemStockDOMapper itemStockDOMapper;

    @Autowired
    private RedisService redisService;

    @Autowired
    private RocketMQProducerService rocketMQProducerService;


    private static final String ITEM_VALIDATE_PREFIX = "item_validate_";

    private ItemDO convertItemDOFromItemModel(ItemModel itemModel){
        if(itemModel == null){
            return null;
        }
        ItemDO itemDO = new ItemDO();
        BeanUtils.copyProperties(itemModel,itemDO);
        itemDO.setPrice(itemModel.getPrice().doubleValue());
        return itemDO;
    }

    private ItemStockDO convertItemStockDOFromItemModel(ItemModel itemModel){
        if(itemModel == null){
            return null;
        }
        ItemStockDO itemStockDO = new ItemStockDO();
        itemStockDO.setItemId(itemModel.getId());
        itemStockDO.setStock(itemModel.getStock());
        return itemStockDO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public ItemModel createItem(ItemModel itemModel) throws BusinessException {
        //校验入参
        ValidationResult result = validator.validate(itemModel);
        if(result.isHasErrors()){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,result.getErrMsg());
        }

        //转化itemmodel->top.easyblog.seckill.model.entity
        ItemDO itemDO = this.convertItemDOFromItemModel(itemModel);

        //写入数据库
        itemDOMapper.insertSelective(itemDO);
        itemModel.setId(itemDO.getId());

        ItemStockDO itemStockDO = this.convertItemStockDOFromItemModel(itemModel);

        itemStockDOMapper.insertSelective(itemStockDO);

        //返回创建完成的对象
        return this.getItemById(itemModel.getId());
    }

    @Override
    public List<ItemModel> listItem() {
        List<ItemDO> itemDOList = itemDOMapper.listItem();
        List<ItemModel> itemModelList =  itemDOList.stream().map(itemDO -> {
            ItemStockDO itemStockDO = itemStockDOMapper.selectByItemId(itemDO.getId());
            ItemModel itemModel = this.convertModelFromDataObject(itemDO,itemStockDO);
            return itemModel;
        }).collect(Collectors.toList());
        return itemModelList;
    }

    @Override
    public ItemModel getItemById(Integer id) {
        ItemDO itemDO = itemDOMapper.selectByPrimaryKey(id);
        if(itemDO == null){
            return null;
        }
        //操作获得库存数量
        ItemStockDO itemStockDO = itemStockDOMapper.selectByItemId(itemDO.getId());

        //将dataobject->model
        ItemModel itemModel = convertModelFromDataObject(itemDO,itemStockDO);

        //获取活动商品信息
        PromoModel promoModel = promoService.getPromoByItemId(itemModel.getId());
        if(promoModel != null && promoModel.getStatus() != 3){
            itemModel.setPromoModel(promoModel);
        }
        return itemModel;
    }


    /**
     * 优化：优先从Redis缓存中获取商品信息，如果没有再从数据库中查询
     *
     * @param itemId
     * @return
     */
    @Override
    public ItemModel getItemByIdInCache(Integer itemId) {
        if (itemId <= 0) {
            return null;
        }
        ItemModel itemModel = JSON.parseObject((String) redisService.get(ITEM_VALIDATE_PREFIX + itemId, RedisService.RedisDataBaseSelector.DB_0), ItemModel.class);
        if (itemModel == null) {
            itemModel = this.getItemById(itemId);
            //10分钟有效期
            redisService.set(ITEM_VALIDATE_PREFIX + itemId, JSON.toJSONString(itemModel), 600, RedisService.RedisDataBaseSelector.DB_0);
        }
        return itemModel;
    }

    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public boolean decreaseStock(Integer itemId, Integer amount) throws BusinessException {
        //直接从Redis缓存中扣减库存
        int stock = (int) redisService.hdecr(PromoServiceImpl.PROMO_ITEM_MAP, PromoServiceImpl.PROMO_ITEM_STOCK_PREFIX + itemId, 1, RedisService.RedisDataBaseSelector.DB_0);
        if (stock >= 0) {
            //扣减库存成功，接下来通过MQ异步库建数据库的库存
            boolean sendResult = rocketMQProducerService.sendAsyncDecrStockMsg(itemId, amount);
            if (!sendResult) {
                //消息发送事变，需要回复redis的库存
                redisService.hincr(PromoServiceImpl.PROMO_ITEM_MAP, PromoServiceImpl.PROMO_ITEM_STOCK_PREFIX + itemId, 1, RedisService.RedisDataBaseSelector.DB_0);
                return false;
            }
            return true;
        } else {
            //扣减库存失败,需要恢复刚才已经扣减的
            redisService.hincr(PromoServiceImpl.PROMO_ITEM_MAP, PromoServiceImpl.PROMO_ITEM_STOCK_PREFIX + itemId, 1, RedisService.RedisDataBaseSelector.DB_0);
            return false;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public void increaseSales(Integer itemId, Integer amount) throws BusinessException {
        itemDOMapper.increaseSales(itemId,amount);
    }

    private ItemModel convertModelFromDataObject(ItemDO itemDO,ItemStockDO itemStockDO){
        ItemModel itemModel = new ItemModel();
        BeanUtils.copyProperties(itemDO,itemModel);
        itemModel.setPrice(new BigDecimal(itemDO.getPrice()));
        itemModel.setStock(itemStockDO.getStock());

        return itemModel;
    }

}
