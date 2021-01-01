package top.easyblog.seckill.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * @author Huang Xin
 */
@Controller
public class BaseController {

    public static final String CONTENT_TYPE_FORMED="application/x-www-form-urlencoded";

    public static final String USER_LOGIN_TOKEN_REDIS_KEY ="USER_LOGIN_TOKEN_REDIS_KEY";

    /**
     * 用户登录信息最大保存时间（免登陆最大时间）:60天
     */
    public static final int MAX_USER_LOGIN_STATUS_KEEP_TIME=60 * 60 * 24 * 60;

   @RequestMapping(value = "/")
   public String index(){
       return "listitem";
   }
}
