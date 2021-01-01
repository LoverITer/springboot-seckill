package top.easyblog.seckill.conf.redis;

/**
 * @author Huang Xin
 * @Description 专门用于管理系统使用到的Redis key的一个类
 * @data 2020/12/23 00:05
 */
public class RedisKeyManager {

    /**
     * 用户登录生成的token的key,格式是：用户ID+随机UUID
     */
    public static final String USER_LOGIN_TOKEN="%d%s";

    /**
     * 存放商品的key的前缀
     */
    public static final String ITEM_KEY_PREFIX = "item_info_cache_";


    public static final String ITEM_LISTS_CACHE = "item_list_cache";

    /**
     * redis商品库存key
     */
    public static final String PROMO_ITEM_STOCK_PREFIX="promo_item_stock_";

    /**
     * 商品售罄的key前缀
     */
    public static final String PROMO_STOCK_INVALID_PREFIX="promo_item_stock_invalid_";

    /**
     * 秒杀Token
     */
    public static final String KILL_TOKEN = "promo_token_promoId_%s_userId_%s_itemId_%s";
    /**
     * 秒杀大闸；为了防止大量无意义的请求进来从而生成大量的Token占用Redis的内存，需要使用一个大闸来显示对某一个商品的最大请求数，这里设置为商品库存的5倍
     */
    public static final String KILL_DOOR_PREFIX = "promo_door_count_";

    /**
     * 验证码key
     */
    public static final String VERIFY_CODE_PREFIX ="verify_code_";

}
