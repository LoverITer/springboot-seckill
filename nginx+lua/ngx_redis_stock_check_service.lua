--秒杀服务前置库存检查，Nginx通过访问redis检查库存，如果商品库存不足直接在nginx就返回了，不用发给后端了
--如果商品库存还有，那就将请求转发给后后端服务器处理

--Redis ip port pwd
local ip="127.0.0.1"
local port =63790
local pwd="95162437hx$"
--秒杀商品库存前缀
local key_prefix="promo_item_stock_invalid_"
--用户IP黑名单
local ip_black_list="ip_black_list"
--用户IP
local user_ip=ngx.var.remote_addr
local user_visit_count="visit_count:"..user_ip

local args=ngx.req.get_uri_args()
if args==nil then
  ngx.say("error request param") 
  return
end

--连接redis
local redis=require("resty.redis")
local red=redis:new()
--set_timeout(connect_timeout,send_timeout,read_timout)
red:set_timeout(3000,3000,3000)
local ok,err=red:connect(ip,port)
if not ok then
   ngx.say("fail to connect Redis["..ip..":"..port.."]")
   return
end

--首先判断用户ip是否在黑名单中
local is_black,err=red:sismember(ip_black_list,user_ip)
if is_black==1 then
  --如果在黑名单中则拒绝处理
  ngx.exit(ngx.HTTP_FORBIDDEN)
end

--用户ip没在黑名单中，将用户访问接口的次数+1，如果1分钟内访问次数超过100次，将用户ip添加到黑名单中
local visit_count,err=red:get(user_visit_count)
if visit_count==ngx.null then
  --用户初次访问
  local res,err=red:set(user_visit_count,1)
  if not res then
     ngx.log(ngx.ERR,err)
     return
  end
  --从用户初次访问到
  res,err=red:expire(user_visit_count,60)
else
  --后续用户再次访问，继续+1
  local res,err=red:incr(user_visit_count)
  if not res then
     ngx.log(ngx.ERR,"incr user visit times failed,case:"..err)
  end
  --用户1分钟内访问多次
  if tonumber(visit_count)>=100  then
    --用户访问次数超过显示次数，将用户ip添加到黑名单中，24小时后恢复
    res,err=red:sadd(ip_black_list,user_ip)
    if not res then
       ngx.log(ngx.ERR,"add ip:"..user_ip.." to black list failed,case:"..err)
       return
    end
    res,err=red:expire(ip_black_list,86400)
  end
end

local item_id=args["itemId"]
local promo_id=args["promoId"]
local token=args["token"]
local amount=args["amount"]
local itemStockOver=red:get(key_prefix..item_id)
if itemStockOver==ngx.null or itemStockOver==nil or not itemStockOver then
   --将请求重定向到后台服务器  
   ngx.exec("/order/back_end/create?itemId="..item_id.."&amount="..amount.."&promoId="..promo_id.."&killToken=0000&token="..token)
else
   --库存不足直接返回
   ngx.say('{"code":20003,"msg":"商品库存不足","data":"手慢一步，宝贝都被别人抢光啦！"}')
end

--put the current redis connection into pool which size is 100
local ok, err = red:set_keepalive(10000, 100)
if not ok then
  ngx.log(ngx.ERR,err)
  ngx.say("failed to set keepalive: ", err)
  return
end
