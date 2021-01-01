package top.easyblog.seckill.service;/**
 * Created by Administrator on 2019/6/22.
 */

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import top.easyblog.seckill.model.dto.MailDto;

import javax.mail.internet.MimeMessage;

/**
 * 邮件服务
 * @Author:debug (SteadyJack)
 * @Date: 2019/6/22 10:09
 **/
@Slf4j
@Service
@EnableAsync
public class MailService {


    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private Environment env;


    /**
     * 发送简单文本文件
     */
    @Async
    public void sendSimpleEmail(final MailDto dto){
        try {
            SimpleMailMessage message=new SimpleMailMessage();
            String messageSendFrom = env.getProperty("mail.send-from");
            if(StringUtils.isEmpty(messageSendFrom)){
                throw new RuntimeException(this.getClass().getDeclaringClass()+".sendSimpleEmail(MailDto) `messageSendFrom` cannot be null or empty");
            }
            message.setFrom(messageSendFrom);
            message.setTo(dto.getSendTo());
            message.setSubject(dto.getSubject());
            message.setText(dto.getContent());
            mailSender.send(message);

            log.info("发送简单文本文件-发送成功!");
        }catch (Exception e){
            log.error("发送简单文本文件-发生异常： ",e.fillInStackTrace());
        }
    }

    /**
     * 发送花哨邮件
     * @param dto
     */
    @Async
    public void sendHTMLMail(final MailDto dto){
        try {
            MimeMessage message=mailSender.createMimeMessage();
            MimeMessageHelper messageHelper=new MimeMessageHelper(message,true,"utf-8");
            String messageSendFrom = env.getProperty("mail.send-from");
            if(StringUtils.isEmpty(messageSendFrom)){
                throw new RuntimeException(this.getClass().getDeclaringClass()+".sendHTMLMail(MailDto) `messageSendFrom` cannot be null or empty");
            }
            messageHelper.setFrom(messageSendFrom);
            messageHelper.setTo(dto.getSendTo());
            messageHelper.setSubject(dto.getSubject());
            messageHelper.setText(dto.getContent(),true);

            //mailSender.send(message);
            log.info("发送HTML邮件-发送成功!");
        }catch (Exception e){
            log.error("发送HTML邮件-发生异常： ",e.fillInStackTrace());
        }
    }
}































