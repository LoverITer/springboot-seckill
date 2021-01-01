package top.easyblog.seckill.model.dto;/**
 * Created by Administrator on 2019/6/22.
 */

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.io.Serializable;

/**
 * 邮件DTO
 *
 * @author ：huangxin
 * @modified ：
 * @since ：2020/12/09 21:06
 */
@Data
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class MailDto implements Serializable {
    //邮件主题
    private String subject;
    //接收人
    private String[] sendTo;
    //邮件内容
    private String content;
}