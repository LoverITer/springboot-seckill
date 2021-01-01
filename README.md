

#### 1、Tomcat参数调优

#### 2、分布式Session

应用服务器的高可用架构设计最为理想的是服务无状态，但实际上业务总会有状态的，以session记录用户信息的例子来讲，未登入时，服务器没有记入用户信息的session访问网站都是以游客方式访问的，账号密码登入网站后服务器必须要记录你的用户信息记住你是登入后的状态，以该状态分配给你更多的权限。

目前常用的分布式Session解决方案一种是基于Spring提供的的分布式Session,导入对应的jar包，简单配置一下就可以使用了，这里不再赘述！

另一种方式就是在用户登录的时候删除一个唯一的、不可重复的Token,以这个Token为key,用户的信息为value把它set到Redis中，并在客户端本地保存前端使用localSession保存这个token，每次用户请求需要登录状态的业务的时候带上token，服务器收到token到Redis中检查一下是否有这个token，如果有就表示用户登录了，就继续处理业务；否者表示用户的登录状态过期或者没有登录过，那就将用户重定向到登录页面，然让其登录.

这里基于第二种想法提供一个简单的实现，仅供参考：

```java
@Controller
@RequestMapping("/user")
@CrossOrigin(allowCredentials = "true", allowedHeaders = "*")
public class UserController extends BaseController {

    @Autowired
    private UserService userService;

    @Autowired
    private RedisService redisService;

    /**
     * 用户登录接口，实现分布式session
     *
     * @param telphone
     * @param password
     * @param request
     * @param response
     * @return
     * @throws BusinessException
     * @throws UnsupportedEncodingException
     * @throws NoSuchAlgorithmException
     */
    @RequestMapping(value = "/login", method = {RequestMethod.POST}, consumes = {CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonResponse<String> login(@RequestParam(name = "telphone") String telphone,
                                        @RequestParam(name = "password") String password,
                                        HttpServletRequest request, HttpServletResponse response) throws BusinessException, UnsupportedEncodingException, NoSuchAlgorithmException {

        //入参校验
        if (StringUtils.isEmpty(telphone) ||
                StringUtils.isEmpty(password)) {
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR);
        }
        //用户登陆服务,用来校验用户登陆是否合法
        UserModel userModel = userService.validateLogin(telphone, this.EncodeByMd5(password));
        //使用UUID+用户ID拼接用户登录Token 
        String userLoginToken = String.format("%d%s", userModel.getId(), UUID.randomUUID().toString().replaceAll("-", ""));

        Boolean res = redisService.setnx(userLoginToken, JSON.toJSONString(userModel), RedisService.RedisDataBaseSelector.DB_0);
        if (res == null) {
            return CommonResponse.create(AppResponseCode.USER_LOGIN_FAILE, "服务异常，请稍后重试！");
        } else if (!res) {
            return CommonResponse.create(AppResponseCode.USER_LOGIN_REPEAT, "您已登录,请不要重复登录");
        }
        redisService.expire(userLoginToken, MAX_USER_LOGIN_STATUS_KEEP_TIME, RedisService.RedisDataBaseSelector.DB_0);
        //将Token使用JSON带给前端
        return CommonResponse.create(AppResponseCode.USER_LOGIN_SUCCESS, userLoginToken);
    }

}
```

```html
<html lang="en" xmlns="http://www.w3.org/1999/xhtml"
	  xmlns:th="http://www.thymeleaf.org">
<head>
	<meta charset="UTF-8">
	<link th:href="@{/static/assets/global/plugins/bootstrap/css/bootstrap.min.css}" rel="stylesheet" type="text/css"/>
	<link th:href="@{/static/assets/global/css/components.css}" rel="stylesheet" type="text/css"/>
	<link th:href="@{/static/assets/admin/pages/css/login.css}" rel="stylesheet" type="text/css"/>
	<script th:src="@{/static/assets/global/plugins/jquery-1.11.0.min.js}" type="text/javascript"></script>
	<script th:src="@{/static/common.js}"></script>
</head>

<body class="login">
	<div class="content">
		<h3 class="form-title">用户登陆</h3>
		<div class="form-group">
			<label class="control-label">手机号</label>
			<div>
				<input  class="form-control" type="text" placeholder="手机号" name="telphone" id="telphone"/>
			</div>	
		</div>
		<div class="form-group">
		<label class="control-label">密码</label>
			<div>
				<input  class="form-control" type="password" placeholder="密码" name="password" id="password"/>
			</div>	
		</div>			
		<div class="form-actions">
			<button class="btn blue" id="login" type="submit">
				登陆
			</button>	
			<button class="btn green" id="register" type="submit">
				注册
			</button>	
		</div>	
	</div>	
</body>
<script>
	$(function(){
		$("#register").on("click",function(){
			window.location.href="/getotp.html";
		});

		$("#login").on("click",function(){
			var telphone = $("#telphone").val();
			var password = $("#password").val();
			if(telphone == null || telphone == ""){
				alert("手机号不能为空");
				return false;
			}
			if(password == null || password == ""){
				alert("密码不能为空");
				return false;
			}
	

			$.ajax({
				type:"POST",
				contentType:"application/x-www-form-urlencoded",
				url:"http://localhost/user/login",
				data:{
					"telphone":$("#telphone").val(),
					"password":password
				},
				xhrFields:{withCredentials:true},
				success:function(data){
					if(data.code==30006){
                        //将用户登录之后的Token存放在localStorage中
                        //USER_LOGIN_TOKEN 是在公共js文件中定义的一个标志
						localStorage.setItem(USER_LOGIN_TOKEN,data.data);
						alert("登陆成功");
						window.location.href="/";
					}else{
						alert("登陆失败，原因为"+data.msg);
					}
				},
				error:function(data){
					alert("登陆失败，原因为"+data.msg);
				}
			});
			return false;
		});
	});
</script>
</html>
```

`验证身份`
在需要登录状态的业务中通过前端将用户登录token传给服务器

```html
<html lang="en" xmlns="http://www.w3.org/1999/xhtml"
      xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <link th:href="@{/static/assets/global/plugins/bootstrap/css/bootstrap.min.css}" rel="stylesheet" type="text/css"/>
    <link th:href="@{/static/assets/global/css/components.css}" rel="stylesheet" type="text/css"/>
    <link th:href="@{/static/assets/admin/pages/css/login.css}" rel="stylesheet" type="text/css"/>
    <script th:src="@{/static/assets/global/plugins/jquery-1.11.0.min.js}" type="text/javascript"></script>
    <script th:src="@{/static/common.js}"></script>

</head>

<body class="login">
<div class="content">
    <h3 class="form-title">商品详情</h3>
    <div id="promoStartDateContainer" class="form-group">
        <label style="color:blue" id="promoStatus" class="control-label"></label>
        <div>
            <label style="color:red" class="control-label" id="promoStartDate"/>
        </div>
    </div>
    <div class="form-group">
        <div>
            <label class="control-label" id="title"/>
        </div>
    </div>
    <div class="form-group">
        <label class="control-label">商品描述</label>
        <div>
            <label class="control-label" id="description"/>
        </div>
    </div>
    <div id="normalPriceContainer" class="form-group">
        <label class="control-label">价格</label>
        <div>
            <label class="control-label" id="price"/>
        </div>
    </div>
    <div id="promoPriceContainer" class="form-group">
        <label style="color:red" class="control-label">秒杀价格</label>
        <div>
            <label style="color:red" class="control-label" id="promoPrice"/>
        </div>
    </div>
    <div class="form-group">
        <div>
            <img style="width:200px;height:auto" id="imgUrl"/>
        </div>
    </div>
    <div class="form-group">
        <label class="control-label">库存</label>
        <div>
            <label class="control-label" id="stock"/>
        </div>
    </div>
    <div class="form-group">
        <label class="control-label">销量</label>
        <div>
            <label class="control-label" id="sales"/>
        </div>
    </div>
    <div class="form-actions">
        <button class="btn blue" id="createorder" type="submit">
            下单
        </button>
    </div>
</div>
</body>
<script>
    function getParam(paramName) {
        paramValue = "", isFound = !1;
        if (this.location.search.indexOf("?") == 0 && this.location.search.indexOf("=") > 1) {
            arrSource = unescape(this.location.search).substring(1, this.location.search.length).split("&"), i = 0;
            while (i < arrSource.length && !isFound) arrSource[i].indexOf("=") > 0 && arrSource[i].split("=")[0].toLowerCase() == paramName.toLowerCase() && (paramValue = arrSource[i].split("=")[1], isFound = !0), i++
        }
        return paramValue == "" && (paramValue = null), paramValue
    }

    var g_itemVO = {};
    $(function () {
        $("#createorder").on("click", function () {
            $.ajax({
                type: "POST",
                contentType: "application/x-www-form-urlencoded",
                url: "http://localhost/order/create",
                data: {
                    "itemId": g_itemVO.id,
                    "amount": 1,
                    "promoId": g_itemVO.promoId,
                     //从客户端本地获取用户登录token,上传给服务器以验证身份
                    "token":localStorage.getItem(USER_LOGIN_TOKEN)
                },
                xhrFields: {withCredentials: true},
                success: function (data) {
                    if (data.code == 200) {
                        alert("下单成功");
                        window.location.reload();
                    } else {
                        alert("下单失败，原因为" + data.msg);
                        if (data.code == 30005) {
                            window.location.href = "/login.html";
                        }
                    }
                },
                error: function (data) {
                    alert("下单失败，原因为" + data.msg);
                }
            });

        });

        //获取商品详情
        $.ajax({
            type: "GET",
            url: "http://localhost/item/get",
            data: {
                "id": getParam("id"),
            },
            xhrFields: {withCredentials: true},
            success: function (data) {
                if (data.code = 200) {
                    g_itemVO = data.data;
                    //渲染物品详情的方法，由于篇幅原因这里先不用管
                    reloadDom();
                    setInterval(reloadDom, 1000);
                } else {
                    alert("获取信息失败，原因为" + data.msg);
                }
            },
            error: function (data) {
                alert("获取信息失败，原因为" + data.msg);
            }
        });

    });
</script>
</html>
```

`秒杀接口服务端端验证用户登录状态`
```java
@Slf4j
@Controller
@RequestMapping("/order")
@CrossOrigin(origins = {"*"},allowCredentials = "true")
public class OrderController extends BaseController {
    @Autowired
    private OrderService orderService;
    
    @Autowired
    private RedisService redisService;


    /**
     * 下单接口
     *
     * @param itemId
     * @param amount
     * @param promoId
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

        //......处理其他业务
    }
}
```

#### 3、基于 Guava Cache + Redis  实现分布式二级缓存方案

二级缓存结构：

1、`L1`：一级缓存，内存缓存，Caffeine 和 Guava Cache。

2、`L2`：二级缓存，集中式缓存，支持Redis。

由于大量的缓存读取会导致 L2 的网络成为整个系统的瓶颈，因此 L1 的目标是降低对 L2 的读取次数。避免使用独立缓存系统所带来的网络IO开销问题。

L2 可以避免应用重启后导致的 L1数据丢失的问题，同时无需担心L1会增加太多的内存消耗，因为你可以设置 L1中缓存数据的数量。

**注意**
> 二级缓存在满足高并发的同时也引入了一些新的问题，比如怎么保证分布式场景下各个节点中`本地缓存的一致性问题`，本项目采用`数据变更通知+定期刷新过期缓存`的策略来尽可能的保证缓存的一致性。具体见下文中的 分布式缓存同步 和 分布式缓存一致性保证 两个章节。

**分布式缓存同步**

首先要搞清楚同步的目的：即是为了尽可能保证分布式缓存的一致性。目前支持通过Redis 和 RocketMQ 的发布订阅功能来实现分布式缓存下不同节点本地缓存的同步。当然该项目留好扩展点，可以快速便捷的扩展其他MQ来实现缓存同步。


**缓存更新**

缓存更新包含了对本地 Guava Cache 和 redis的操作，同时会通知其他缓存节点进行缓存更新操作。


#####  3.1 Redis缓存



##### 3.2 本地数据热点缓存

Google Guava Cache 本地热点缓存是一种非常优秀本地缓存解决方案，提供了基于容量，时间和引用的缓存回收方式。基于容量的方式内部实现采用LRU算法，基于引用回收很好的利用了Java虚拟机的垃圾回收机制。其中的缓存构造器CacheBuilder采用构建者模式提供了设置好各种参数的缓存对象，缓存核心类LocalCache里面的内部类Segment与jdk1.7及以前的ConcurrentHashMap非常相似，都继承于ReetrantLock，还有六个队列，以实现丰富的本地缓存方案。



#### 在大型的应用集群中若对Redis访问过度依赖，会否产生应用服务器到Redis之间的网络带宽产生瓶颈？若会产生瓶颈，如何解决这样的问题？

(1)如果nginx服务器内存还算充裕，热点数据估量可以承受的话，可以使用nginx的 lua sharedic来降低redis的依赖

(2)如果单台nginx内存不足，则采用 lvs+keepalived+ n 台nginx服务器对内存进行横向拓展

(3)如果lua sharedic成本过高无法承受，则将redis改造为cluster架构，应用集群只连接到n台slave上来均摊网络带宽消耗，且使redis集群的各主机尽量不处在同一个机房或网段，避免使用同一个出入口导致网络带宽瓶颈


#### 4、扣减库存缓存化和异步化

初始方案：
* （1）活动开始之前将库存数据同步到Redis缓存中
* （2）活动开始后用户下单直接从Redis中判断库存并减库存
* （3）产生订单，保存订单到数据库

问题：

其实这一套方案是根本不能在线上使用的，因为上面的方案在执行过程中**Redis中的数据和数据库中的严重不一致**，如果活动中Redis数据库被压垮了，那么将会造成重大损失。
分析问题的关节就在缓存的数据和数据库的数据不一致，我们想把办法让他们一直不就好了，为了不影响性能，我们可以考虑使用在缓存中的库存被扣减之后异步的扣减数据库的库存，考虑到异步，我们就想到了MQ，因此扣减库存的方案修改为：

优化方案：
* （1）活动开始之前将库存数据同步到Redis缓存中
* （2）活动开始后用户下单直接从Redis中判断库存并减库存
* （3）产生订单，保存订单到数据库
* （4）异步消息扣减数据库内存

可是这个想法真的就那么靠谱吗？其实不然，即使在Redis中扣减库存成功了,然而在产生订单的时候也会出现异常啊，如果出现异常那数据库之前的操作都会随着本地事务回滚，
这个时候就会造成商家商家库存是减少了，但是却不见订单的情况，为了解决这种问题，我们可以使用RocketMQ体统的事物消息，即先发送一个异步同步数据库课程的消息，但是则而过消息不能被消费者看到，是具有事物的，只有单订单成功成功并且成功在数据库保存提交之后再让消费者去消费这个消息,下面是具体的代码实现，仅供参考：

`下单业务`
```java
@Service
public class OrderServiceImpl implements OrderService {


    @Autowired
    private ItemService itemService;


    @Autowired
    private OrderDOMapper orderDOMapper;

    @Autowired
    private IDGenerationService idGenerationService;


    /**
     * 用户对同一件商品购买数量的限制
     */
    private static final Integer USER_PURCHASE_AMOUNT_IN_ONE_ITEM_LIMIT = 2;


    /**
     * 秒杀下单核心业务逻辑
     *
     * @param userId
     * @param itemId
     * @param promoId
     * @param amount
     * @return
     * @throws BusinessException
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderModel createOrder(Integer userId, Integer itemId, Integer promoId, Integer amount) throws BusinessException {

        /*1. 购买数量前置检查
         * 首先需要校验购买的数量是大于0，不能产生一个空的或无效的订单
         * 其次，用户可能多次下单，因此需要限制一个用户对同一个产品无论下单多少次，都只能购买2件
         * 在者需要验证用户下单时所需购买量 库存是否满足
         */
        Integer purchaseAmount = orderDOMapper.selectUserPurchaseAmount(userId, itemId);
        /*if (amount <= 0 || purchaseAmount >= USER_PURCHASE_AMOUNT_IN_ONE_ITEM_LIMIT ||
                amount + purchaseAmount > USER_PURCHASE_AMOUNT_IN_ONE_ITEM_LIMIT) {
            throw new BusinessException(EmBusinessError.ITEM_USER_KILLED, "同一件商品购买数量不能超过两件");
        }*/

        //2.校验下单状态,下单的商品是否存在，购买数量是否足够
        ItemModel itemModel = itemService.getItemByIdInCache(itemId);
        if (itemModel == null || itemModel.getStock() < 0) {
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "商品不存在或者未在秒杀时间段");
        }

        /*
         * 3. 购买数量后置检查
         * 需要验证用户下单时所需购买量 库存是否满足
         */
        if (itemModel.getStock() - amount < 0) {
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "用户抢购物品数量不正确");
        }

        /*
         *4. 校验活动信息
         * 主要是检查用户下单时当前商品是否处于秒杀活动状态，通过一个标志位实现
         */
        if(promoId != null){
            if (itemModel.getPromoModel().getStatus() != 2) {
                //校验活动是否正在进行中
                throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "秒杀活动还未开始，请稍等！");
            }
        }

        //5. 落单减库存 ，只是预减库存
        boolean result = itemService.decreaseStock(itemId,amount);
        if(!result){
            throw new BusinessException(EmBusinessError.STOCK_NOT_ENOUGH);
        }

        //6. 预订单入库
        OrderModel orderModel = new OrderModel();
        orderModel.setUserId(userId);
        orderModel.setItemId(itemId);
        orderModel.setAmount(amount);
        if(promoId != null){
            orderModel.setItemPrice(itemModel.getPromoModel().getPromoItemPrice());
        }else{
            orderModel.setItemPrice(itemModel.getPrice());
        }
        orderModel.setPromoId(promoId);
        orderModel.setOrderPrice(orderModel.getItemPrice().multiply(new BigDecimal(amount)));

        //使用雪花算法生成交易流水号,订单号
        orderModel.setId(String.valueOf(idGenerationService.nextId()));
        OrderDO orderDO = convertFromOrderModel(orderModel);
        orderDOMapper.insertSelective(orderDO);

        //7. 增加商品的销量
        itemService.increaseSales(itemId,amount);

        /*
        * 8. 返回订单给前端，后续交给支付模块处理
        * 这里为了防止用户超时为支付，需要使用MQ实现用户超时之后库存恢复的过程
         */

        return orderModel;
    }

}
```

`事物型异步扣减数据库库存消息生产者`
```java
/**
 *
 * @author      Huang Xin
 * @Description 使用事务型消息，保证异步扣减库存可以在订单事务完成之后进行，如果订单事物发生异常，则异步扣减库存的消息也会回滚
 * @data        2020/12/21 17:36
 */
@Slf4j
@Component
@RocketMQTransactionListener(txProducerGroup = "kill-async-producer-group")
public class TransactionOrderMQProducer implements TransactionListener {


    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    @Value(value = "${mq.async-stock.topic}")
    private String decrStockTopic;

    @Autowired
    private OrderService orderService;

    /**
     * 事务型异步发送扣减数据库库存的消息
     * @param itemId
     * @param amount
     * @return
     */
    public boolean asyncSendDecrStockMsgInTransaction(Integer itemId,Integer amount,Integer promoId,Integer userId)throws BusinessException{
        Map<String,Integer> orderInfo = new HashMap<>(16);
        orderInfo.put("itemId",itemId);
        orderInfo.put("amount",amount);

        Map<String,Integer> argsMap=new HashMap<>(16);
        argsMap.put("itemId",itemId);
        argsMap.put("amount",amount);
        argsMap.put("promoId",promoId);
        argsMap.put("userId",userId);
        try{
            Message message = new Message(decrStockTopic, JSON.toJSON(orderInfo).toString().getBytes(StandardCharsets.UTF_8));
            TransactionMQProducer producer = (TransactionMQProducer)rocketMQTemplate.getProducer();
            //发送事务消息：下单成功并且数据库事务提交之后异步扣减库存
            TransactionSendResult result = producer.sendMessageInTransaction(message, argsMap);
            if(result.getLocalTransactionState()== LocalTransactionState.COMMIT_MESSAGE){
                log.info("发送异步扣减库存消息成功.orderInfo:{}",orderInfo);
                return true;
            }else{
                throw new BusinessException(AppResponseCode.MQ_SEND_MESSAGE_FAIL);
            }
        }catch (Exception e){
            log.error("发送扣减数据库库存消息异常，orderInfo:{},case:{}", orderInfo, e.getMessage());
            return false;
        }
    }


    /**
     * 检查本地事务，如果下单这一串的操作都执行成功之后爱提交消息扣减库存
     *
     * @param message
     * @param args
     * @return COMMIT_MESSAGE   本地事务执行成功，提交消息让消费者消费
     *         ROLLBACK_MESSAGE 本地事务执行失败，回滚消息（相当于没有发送消息）
     *         UNKNOW            未知状态，还需要检查
     */
    @Override
    public LocalTransactionState executeLocalTransaction(Message message, Object args) {
        //处理真正的业务  创建订单
        Integer userId=(Integer)((Map)args).get("userId");
        Integer amount=(Integer)((Map)args).get("amount");
        Integer promoId=(Integer)((Map)args).get("promoId");
        Integer itemId=(Integer)((Map)args).get("itemId");
        try {
            orderService.createOrder(userId,itemId,promoId,amount);
        } catch (BusinessException e) {
            //本地数据库事务失败，回滚消息
            log.error("创建订单失败，即将回滚消息，case:{}",e.getMessage());
            return LocalTransactionState.ROLLBACK_MESSAGE;
        }
        return LocalTransactionState.COMMIT_MESSAGE;
    }

    @Override
    public LocalTransactionState checkLocalTransaction(MessageExt messageExt) {
        return null;
    }
}
```

`事物型异步扣减数据库库存消息消费者`
```java
/**
 * 秒杀订单异步扣减库存
 *
 * @author ：huangxin
 * @modified ：
 * @since ：2020/12/11 14:27
 */
@Slf4j
@Component
@RocketMQMessageListener(
        consumerGroup = "${mq.async-stock.consumer.group}",
        topic = "${mq.async-stock.topic}",
        selectorType = SelectorType.TAG,
        selectorExpression = "*")
public class KillOrderAsyncDecrStockListener implements RocketMQListener<MessageExt> {


    @Autowired
    private ItemStockDOMapper itemStockDOMapper;

    @Autowired
    private ItemService itemService;


    /**
     * 实现库存到数据库的真正扣减
     *
     * @param msg
     */
    @Override
    public void onMessage(MessageExt msg) {
        try {
            log.info("收到异步扣减库存消息,{}", msg);
            //接收订单消息并解析处理
            Map<String, Integer> orderInfo = JSON.parseObject(new String(msg.getBody(), StandardCharsets.UTF_8), Map.class);
            if (orderInfo == null) {
                return;
            }
            itemStockDOMapper.decreaseStock(orderInfo.get("itemId"), orderInfo.get("amount"));
            itemService.increaseSales(orderInfo.get("itemId"), orderInfo.get("amount"));
            log.info("异步扣减库存成功");
        } catch (Exception e) {
            e.printStackTrace();
            log.error("处理扣减库存失败，msg:{}", e.getMessage());
        }
    }

}
```

`Controller层秒杀接口优化`
```java
@Slf4j
@Controller("order")
@RequestMapping("/order")
@CrossOrigin(origins = {"*"},allowCredentials = "true")
public class OrderController extends BaseController {
    @Autowired
    private OrderService orderService;
    
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
            //不直接调用service层的订单接口，可以通过MQ异步创建订单
            //OrderModel orderModel = orderService.createOrder(userModel.getId(),itemId,promoId,amount);
            orderMQProducer.asyncSendDecrStockMsgInTransaction(itemId,amount,promoId,userModel.getId());
            return CommonResponse.create(AppResponseCode.SUCCESS,"下单成功");
        }catch (BusinessException e){
            log.error(e.getMessage());
            return CommonResponse.create(AppResponseCode.FAIL,e.getMessage());
        }
    }
}
```


#### 5、扣减同步数据库的问题

上面扣减库存异步化的操作看似非常完美,但是却有非常多的异常问题:

* 异步扣减库存的消息发送失败
* 数据库扣减库存的时候发生异常导致失败
* 下单失败却无法真确回补库存

##### 库存数据库最终一致性保证

方案：

* 引入库存操作流水
* 引入事务型消息机制 

问题：

* Redis不可用时如何处理
* 扣减流水失败如何处理

**业务场景决定高可用技术实现方案**

本系统设计原则：

* 宁可少买，也不能超卖

方案：

* 超时订单释放
* Redis中的库存数据可以比实际数据库中库存少

#### 6、库存售罄模型

* 库存售罄标识
* 售罄后不再执行后续操作
* 售罄后通知各系统此商售罄
* 回补上新


#### 7、流量削峰技术

方案：
* 秒杀令牌的原理和使用
* 秒杀大闸的原理和使用
* MQ泄洪的原理和使用

##### 7.1 秒杀令牌原理

* 秒杀接口需要依靠令牌才能进入
* 秒杀的令牌由秒杀活动模块负责动态生成
* 秒杀活动模块对秒杀令牌生成全权处理，逻辑收口
* 秒杀下单前需要先获取秒杀令牌 

**生成令牌**

生成令牌的时候需要验证

##### 7.2 秒杀大闸原理

##### 7.3 MQ泄洪原理

1. 排队有些时候比并发更高效（Redis单线程模型,innodb mutex key等）
2. 依靠排队去限制并发流量
3. 依靠排队和下游拥塞窗口程度调整队列释放流量的大小

##### 7.4 MQ流量削峰

![](http://image.easyblog.top/QQ%E6%B5%8F%E8%A7%88%E5%99%A8%E6%88%AA%E5%9B%BE20201223111651.png)

消息高可用对MQ的可用提出了极高的要求，对于一个秒杀服务，使用MQ来异步削峰**如何保证全链路消息不丢失**是其中一个比较重要的问题，既要体现在：

1. 生产者发送消息到MQ有可能丢失消息
3. MQ接收到消息后，写入硬盘时消息丢失
3. MQ接收到消息后，写入硬盘后，硬盘损坏，也有可能丢失消息
4. 消费者消息MQ，如果进行一步消费，也有可能丢失消息


**路由中心（nameServer）挂了怎么办？**

可以考虑在发送消息经过一定重试次数和等待时间之后如果消息还没有发送成功，那就将消息存储暂时存储在本地，比如存储在一个文本文件中，之后待服务中心恢复之后再启动一个定时扫描的线程，扫描本地文本文件中的消息并发送到MQ中


**生产者发送消息到MQ消息丢失**

方案1：同步发送+多次重试，最通用的方案

方案2：使用RocketMQ提供的事物型消息机制（目前RocketMQ独有的,以性能换取安全性）

![](http://image.easyblog.top/QQ%E6%B5%8F%E8%A7%88%E5%99%A8%E6%88%AA%E5%9B%BE20201223121434.png)

(1)为什么要发送这个half消息?

(2)half消息发送失败咋办?

(3)如果half发送成功，但是没有对应的响应咋办?

(4)half消息如何做到对消费者不可见的?

![](http://image.easyblog.top/QQ%E6%B5%8F%E8%A7%88%E5%99%A8%E6%88%AA%E5%9B%BE20201223124108.png)

(4)订单系统写数据库失败，咋办?

(5)下单成功只有如何等待支付成功?
