package top.easyblog.seckill.service.impl;

import com.alibaba.fastjson.JSON;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import top.easyblog.seckill.cache.utils.RandomUtil;
import top.easyblog.seckill.conf.redis.RedisKeyManager;
import top.easyblog.seckill.error.BusinessException;
import top.easyblog.seckill.error.EmBusinessError;
import top.easyblog.seckill.model.ItemModel;
import top.easyblog.seckill.model.PromoModel;
import top.easyblog.seckill.model.dto.OrderDto;
import top.easyblog.seckill.model.entity.ItemDO;
import top.easyblog.seckill.model.entity.ItemStockDO;
import top.easyblog.seckill.model.entity.StockLogDO;
import top.easyblog.seckill.model.mapper.ItemDOMapper;
import top.easyblog.seckill.model.mapper.ItemStockDOMapper;
import top.easyblog.seckill.model.mapper.StockLogDOMapper;
import top.easyblog.seckill.service.ItemService;
import top.easyblog.seckill.service.LocalCacheService;
import top.easyblog.seckill.service.PromoService;
import top.easyblog.seckill.service.RedisService;
import top.easyblog.seckill.service.mq.RocketMQProducerService;
import top.easyblog.seckill.validator.ValidationResult;
import top.easyblog.seckill.validator.ValidatorImpl;

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
    private ItemStockDOMapper itemStockMapper;

    @Autowired
    private StockLogDOMapper stockLogMapper;

    @Autowired
    private RedisService redisService;

    @Autowired
    private RocketMQProducerService rocketMQProducerService;

    @Autowired
    private LocalCacheService localCacheService;


    private ItemDO convertItemDOFromItemModel(ItemModel itemModel) {
        if (itemModel == null) {
            return null;
        }
        ItemDO itemDO = new ItemDO();
        BeanUtils.copyProperties(itemModel, itemDO);
        itemDO.setPrice(itemModel.getPrice().doubleValue());
        return itemDO;
    }

    private ItemStockDO convertItemStockDOFromItemModel(ItemModel itemModel) {
        if (itemModel == null) {
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
        if (result.isHasErrors()) {
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, result.getErrMsg());
        }

        //转化itemmodel->top.easyblog.seckill.server.model.entity
        ItemDO itemDO = this.convertItemDOFromItemModel(itemModel);

        //写入数据库
        itemDOMapper.insertSelective(itemDO);
        itemModel.setId(itemDO.getId());

        ItemStockDO itemStockDO = this.convertItemStockDOFromItemModel(itemModel);

        itemStockMapper.insertSelective(itemStockDO);

        //返回创建完成的对象
        return this.getItemById(itemModel.getId());
    }

    @Override
    public List<ItemModel> listItem() {
        List<ItemDO> itemDOList = itemDOMapper.listItem();
        List<ItemModel> itemModelList = itemDOList.stream().map(itemDO -> {
            ItemStockDO itemStockDO = itemStockMapper.selectByItemId(itemDO.getId());
            ItemModel itemModel = this.convertModelFromDataObject(itemDO, itemStockDO);
            return itemModel;
        }).collect(Collectors.toList());
        return itemModelList;
    }

    @Override
    public ItemModel getItemById(Integer id) {
        ItemDO itemDO = itemDOMapper.selectByPrimaryKey(id);
        if (itemDO == null) {
            return null;
        }
        //操作获得库存数量
        ItemStockDO itemStockDO = itemStockMapper.selectByItemId(itemDO.getId());

        //将dataobject->model
        ItemModel itemModel = convertModelFromDataObject(itemDO, itemStockDO);

        //获取活动商品信息
        PromoModel promoModel = promoService.getPromoByItemId(itemModel.getId());
        if (promoModel != null && promoModel.getStatus() != 3) {
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
        if (itemId == null || itemId <= 0) {
            return null;
        }
        //从本地缓存获取数据,本地缓存没有了，在从redis缓存中获取，redis再没有就从数据库查询
        ItemModel itemModel = JSON.toJavaObject(localCacheService.get(RedisKeyManager.ITEM_KEY_PREFIX + itemId), ItemModel.class);
        if (itemModel == null) {
            itemModel = JSON.parseObject((String) redisService.get(RedisKeyManager.ITEM_KEY_PREFIX + itemId, RedisService.RedisDataBaseSelector.DB_0), ItemModel.class);
            if (itemModel == null) {
                itemModel = this.getItemById(itemId);
                redisService.set(RedisKeyManager.ITEM_KEY_PREFIX + itemId, JSON.toJSONString(itemModel), RedisService.RedisDataBaseSelector.DB_0);
            }
            localCacheService.set(RedisKeyManager.ITEM_KEY_PREFIX + itemId, (JSON) JSON.toJSON(itemModel));
        }
        return itemModel;
    }

    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public boolean decreaseStock(Integer itemId, Integer amount) {
        //直接从Redis缓存中扣减库存
        long stock = redisService.decr(RedisKeyManager.PROMO_ITEM_STOCK_PREFIX + itemId, amount, RedisService.RedisDataBaseSelector.DB_0);
        if (stock > 0) {
            //扣减库存成功
            return true;
        } else if (stock == 0) {
            //库存售罄，在Redis中设置一个标志
            redisService.set(RedisKeyManager.PROMO_STOCK_INVALID_PREFIX + itemId, "true", RedisService.RedisDataBaseSelector.DB_0);
            return true;
        } else {
            //扣减库存失败,需要恢复刚才已经扣减的
            redisService.incr(RedisKeyManager.PROMO_ITEM_STOCK_PREFIX + itemId, amount, RedisService.RedisDataBaseSelector.DB_0);
            return false;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public void increaseSales(Integer itemId, Integer amount) throws BusinessException {
        itemDOMapper.increaseSales(itemId, amount);
    }


    @Override
    public String initStockLog(OrderDto orderDto) {
        if(orderDto==null){
            throw new RuntimeException("param `orderDto` can not be null");
        }
        StockLogDO stockLogDO = new StockLogDO();
        stockLogDO.setOrderId(orderDto.getOrderId());
        stockLogDO.setItemId(orderDto.getItemId());
        stockLogDO.setAmount(orderDto.getAmount());
        stockLogDO.setStockLogId(RandomUtil.getUUID());
        //初始化对应的库存流水
        stockLogDO.setStatus(1);
        //将初始库存流水插入数据库
        stockLogMapper.insertSelective(stockLogDO);
        return stockLogDO.getStockLogId();
    }

    private ItemModel convertModelFromDataObject(ItemDO itemDO, ItemStockDO itemStockDO) {
        ItemModel itemModel = new ItemModel();
        BeanUtils.copyProperties(itemDO, itemModel);
        itemModel.setPrice(new BigDecimal(itemDO.getPrice()));
        itemModel.setStock(itemStockDO.getStock());

        return itemModel;
    }

}
