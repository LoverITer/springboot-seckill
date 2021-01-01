package top.easyblog.seckill.conf.mq;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import top.easyblog.seckill.service.MailService;
import top.easyblog.seckill.service.RedisService;

/**
 * 处理用户下单成后通过MQ异步发送邮件的服务
 *
 * @author ：huangxin
 * @modified ：
 * @since ：2020/12/11 14:27
 */
@Slf4j
@Component
@RocketMQMessageListener(
        consumerGroup = "${mq.email.consumer.group}",
        topic = "${mq.email.topic}")
public class KillOrderEmailMQListener implements RocketMQListener<MessageExt> {

    @Autowired
    private MailService mailService;

    @Autowired
    private Environment env;

    @Autowired
    private RedisService redisService;




    /**
     * 在这里处理消息,通过MQ异步发送消息给用户
     *
     * @param msg
     */
    @Override
    public void onMessage(MessageExt msg) {
      /* try{
           //接收订单消息并解析处理
           KillSuccessUserInfo orderInfo = JSON.parseObject(new String(msg.getBody(), StandardCharsets.UTF_8), KillSuccessUserInfo.class);
           if(orderInfo==null) {
               return;
           }
           log.info("接收到异步发送邮件的消息：{},开始处理", orderInfo.toString());
           //读取配置文件中定义的邮件预处理正文
           String preContent=env.getProperty("mail.kill.item.success.content");
           if(StringUtils.isEmpty(preContent)){
               throw new RuntimeException(String.format("%s onMessage() param `mail.kill.item.success.content` cannot be null or empty",this.getClass().getDeclaringClass()));
           }
           String hostRootPath= (String)redisService.get("APP-HOST-ROOT-PATH", RedisService.RedisDataBaseSelector.DB_0);
           String orderDetailsUrl= hostRootPath + "kill/order-details/" + orderInfo.getCode();
           //填充邮件正文
           final String content=String.format(preContent,orderInfo.getItemName(),orderDetailsUrl,orderDetailsUrl);
           //读取配置文件中的邮件主题
           String subject=env.getProperty("mail.kill.item.success.subject");
           if(StringUtils.isEmpty(subject)){
               throw new RuntimeException(String.format("%s onMessage() param `mail.kill.item.success.subject` cannot be null or empty",this.getClass().getDeclaringClass()));
           }
           MailDto mailDto=new MailDto(subject,new String[]{orderInfo.getEmail()},content);
           log.info("消息处理成功，开始发送邮件!");
           mailService.sendHTMLMail(mailDto);
       }catch (Exception e){
           e.printStackTrace();
           log.top.easyblog.seckill.server.error("处理异步发送邮件消息异常，msg:{}",e.getMessage());
       }*/
    }
}
