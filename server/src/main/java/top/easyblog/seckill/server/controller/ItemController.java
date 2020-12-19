package top.easyblog.seckill.server.controller;

import com.alibaba.fastjson.JSON;
import org.joda.time.format.DateTimeFormat;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import top.easyblog.seckill.model.vo.ItemVO;
import top.easyblog.seckill.api.error.AppResponseCode;
import top.easyblog.seckill.api.error.BusinessException;
import top.easyblog.seckill.api.response.CommonResponse;
import top.easyblog.seckill.api.service.ItemService;
import top.easyblog.seckill.api.service.LocalCacheService;
import top.easyblog.seckill.server.service.RedisService;
import top.easyblog.seckill.model.ItemModel;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Huangxin
 */
@Controller("/item")
@RequestMapping("/item")
@CrossOrigin(origins = {"*"}, allowCredentials = "true")
public class ItemController extends BaseController {

    /**
     * 存放商品数据的字典的key
     */
    private static final String ITEM_CACHE_MAPPING_KEY = "ITEM_CACHE_MAPPING";

    /**
     * 存放商品的key的前缀
     */
    private static final String ITEM_CACHE_KEY_PREFIX = "ITEM_";


    private static final String ITEM_LISTS_CACHE = "ITEM_LISTS_CACHE";


    @Autowired
    private ItemService itemService;

    @Autowired
    private RedisService redisService;

    @Autowired
    private LocalCacheService localCacheService;


    //创建商品的controller
    @RequestMapping(value = "/create", method = {RequestMethod.POST}, consumes = {CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonResponse<ItemVO> createItem(@RequestParam(name = "title") String title,
                                             @RequestParam(name = "description") String description,
                                             @RequestParam(name = "price") BigDecimal price,
                                             @RequestParam(name = "stock") Integer stock,
                                             @RequestParam(name = "imgUrl") String imgUrl) throws BusinessException {
        //封装service请求用来创建商品
        ItemModel itemModel = new ItemModel();
        itemModel.setTitle(title);
        itemModel.setDescription(description);
        itemModel.setPrice(price);
        itemModel.setStock(stock);
        itemModel.setImgUrl(imgUrl);

        ItemModel itemModelForReturn = itemService.createItem(itemModel);
        String itemCacheKey = String.format("%s%d", ITEM_CACHE_KEY_PREFIX, itemModelForReturn.getId());
        redisService.delete(RedisService.RedisDataBaseSelector.DB_0,ITEM_LISTS_CACHE);
        redisService.hset(ITEM_CACHE_MAPPING_KEY,itemCacheKey, JSON.toJSONString(itemModel), 60 * 10 * 1000, RedisService.RedisDataBaseSelector.DB_0);
        ItemVO itemVO = convertVOFromModel(itemModelForReturn);

        return CommonResponse.create(AppResponseCode.SUCCESS, itemVO);
    }

    //商品详情页浏览
    @RequestMapping(value = "/get", method = {RequestMethod.GET})
    @ResponseBody
    public CommonResponse<ItemVO> getItem(@RequestParam(name = "id") Integer id) {
        String itemCacheKey = String.format("%s%d", ITEM_CACHE_KEY_PREFIX, id);
        //采用多级缓存：首先到本地JVM缓存中查找需要的值，找到直接返回，没找到再去Redis缓存中查询，在没找到就去数据库查询
        ItemModel itemModel = (ItemModel)localCacheService.get(itemCacheKey);
        if(itemModel==null) {
            System.out.println("本地缓存没有命中");
            //先从redis中查询对应商品的信息
            itemModel = JSON.parseObject((String) redisService.hget(ITEM_CACHE_MAPPING_KEY, itemCacheKey, RedisService.RedisDataBaseSelector.DB_0), ItemModel.class);
            if (itemModel == null) {
                //redis中没有对应商品信息，就从数据库查询
                itemModel = itemService.getItemById(id);
                //将商品信息存放到hash中
                redisService.hset(ITEM_CACHE_MAPPING_KEY, itemCacheKey, JSON.toJSONString(itemModel), 60 * 10 * 1000, RedisService.RedisDataBaseSelector.DB_0);
            }
            localCacheService.set(itemCacheKey,itemModel);
        }

        ItemVO itemVO = convertVOFromModel(itemModel);

        return CommonResponse.create(AppResponseCode.SUCCESS, itemVO);

    }

    /**
     * 获取所有商品
     * @return
     */
    @RequestMapping(value = "/list", method = {RequestMethod.GET})
    @ResponseBody
    public CommonResponse<List<ItemVO>> listItem() {

        List<ItemModel> itemModelList = (List<ItemModel> )localCacheService.get(ITEM_LISTS_CACHE);
        if(itemModelList==null) {
            Object obj = redisService.get(ITEM_LISTS_CACHE, RedisService.RedisDataBaseSelector.DB_0);
            String[] itemLists = obj == null ? null : obj.toString().replace("[", "").replace("]", "").split("},");
            if (itemLists != null) {
                itemModelList = new ArrayList<>();
                for (int i = 0; i < itemLists.length; i++) {
                    if (i != itemLists.length - 1) {
                        itemLists[i] += "}";
                    }
                    itemModelList.add(JSON.parseObject(itemLists[i], ItemModel.class));
                }
            } else {
                itemModelList = itemService.listItem();
                redisService.set(ITEM_LISTS_CACHE, JSON.toJSONString(itemModelList), RedisService.RedisDataBaseSelector.DB_0);
            }
            localCacheService.set(ITEM_LISTS_CACHE,itemModelList);
        }
        //使用stream api将list内的itemModel转化为ITEMVO;
        List<ItemVO> itemVOList = itemModelList.stream().map(this::convertVOFromModel).collect(Collectors.toList());
        return CommonResponse.create(AppResponseCode.SUCCESS, itemVOList);
    }


    @RequestMapping(value = "/test", method = {RequestMethod.GET})
    @ResponseBody
    public CommonResponse<String> test(){
        return CommonResponse.create(AppResponseCode.SUCCESS);
    }


    private ItemVO convertVOFromModel(ItemModel itemModel) {
        if (itemModel == null) {
            return null;
        }
        ItemVO itemVO = new ItemVO();
        BeanUtils.copyProperties(itemModel, itemVO);
        if (itemModel.getPromoModel() != null) {
            //有正在进行或即将进行的秒杀活动
            itemVO.setPromoStatus(itemModel.getPromoModel().getStatus());
            itemVO.setPromoId(itemModel.getPromoModel().getId());
            itemVO.setStartDate(itemModel.getPromoModel().getStartDate().toString(DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss")));
            itemVO.setPromoPrice(itemModel.getPromoModel().getPromoItemPrice());
        } else {
            itemVO.setPromoStatus(0);
        }
        return itemVO;
    }
}
