package top.easyblog.seckill.server.conf.mq;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.annotation.SelectorType;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import top.easyblog.seckill.server.service.MailService;

/**
 * 处理超时未支付的订单
 *
 * @author ：huangxin
 * @modified ：
 * @since ：2020/12/11 14:34
 */
@Slf4j
@Component
@RocketMQMessageListener(
        consumerGroup = "${mq.timeout-order.consumer.group}",
        topic = "${mq.timeout-order.topic}",
        selectorType = SelectorType.TAG,
        selectorExpression = "*")
public class KillOrderTimeoutListener implements RocketMQListener<MessageExt> {


    @Autowired
    private Environment env;

    @Autowired
    private MailService mailService;

    @Override
    public void onMessage(MessageExt msg) {
       /* try{
            //接收订单消息并解析处理
            KillSuccessUserInfo orderInfo = JSON.parseObject(new String(msg.getBody(), StandardCharsets.UTF_8), KillSuccessUserInfo.class);
            if(orderInfo!=null){
                log.info("接收到超时未支付订单监控消息：{}", orderInfo.toString());
                //重新重数据库中获取订单的状态，用户可能
                ItemKillSuccess order=itemKillSuccessMapper.selectByPrimaryKey(orderInfo.getCode());
                if(order!=null&& order.getStatus().equals(AppResponseCode.ORDER_PAY_STATUS_NO_PAY.getCode())){
                    log.info("用户秒杀超时未支付订单——开始处理.订单详情：{}",orderInfo.toString());
                    //修改订单状态
                    itemKillSuccessMapper.expireOrder(orderInfo.getCode());
                    //恢复库存
                    itemKillMapper.recoverKillItemStock(orderInfo.getKillId());
                    log.info("用户秒杀超时未支付订单——处理过成功.库存恢复");
                    //读取配置文件中定义的邮件预处理正文
                    String preContent=env.getProperty("mail.kill.item.time-out.content");
                    if(StringUtils.isEmpty(preContent)){
                        throw new RuntimeException(String.format("%s onMessage() param `mail.kill.item.success.content` cannot be null or empty",this.getClass().getDeclaringClass()));
                    }
                    //填充邮件正文
                    final String content=String.format(preContent,orderInfo.getItemName());
                    //读取配置文件中的邮件主题
                    String subject=env.getProperty("mail.kill.item.time-out.subject");
                    if(StringUtils.isEmpty(subject)){
                        throw new RuntimeException(String.format("%s onMessage() param `mail.kill.item.success.subject` cannot be null or empty",this.getClass().getDeclaringClass()));
                    }
                    MailDto mailDto=new MailDto(subject,new String[]{orderInfo.getEmail()},content);
                    log.info("支付订单超时消息处理成功，开始发送邮件!");
                    mailService.sendHTMLMail(mailDto);
                }else{
                    log.info("用户订单未超时-处理完毕：订单详细：{}", orderInfo.toString());
                }
            }
        }catch (Exception e){
            e.printStackTrace();
            log.top.easyblog.seckill.server.error("处理超时订单消息错误，msg:{}",e.getMessage());
        }*/
    }
}
