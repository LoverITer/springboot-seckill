package top.easyblog.seckill.server.controller;

/**
 * Created by hzllb on 2018/11/14.
 */
public class BaseController {

    public static final String CONTENT_TYPE_FORMED="application/x-www-form-urlencoded";

    public static final String USER_SESSION_REDIS_KEY="USER_SESSION_REDIS_KEY";

    /**
     * 用户登录信息最大保存时间（免登陆最大时间）:60天
     */
    public static final int MAX_USER_LOGIN_STATUS_KEEP_TIME=60 * 60 * 24 * 60;

//    //定义exceptionhandler解决未被controller层吸收的exception
//    @ExceptionHandler(Exception.class)
//    @ResponseStatus(HttpStatus.OK)
//    @ResponseBody
//    public Object handlerException(HttpServletRequest request, Exception ex){
//        Map<String,Object> responseData = new HashMap<>();
//        if( ex instanceof BusinessException){
//            BusinessException businessException = (BusinessException)ex;
//            responseData.put("errCode",businessException.getErrCode());
//            responseData.put("errMsg",businessException.getErrMsg());
//        }else{
//            responseData.put("errCode", EmBusinessError.UNKNOWN_ERROR.getErrCode());
//            responseData.put("errMsg",EmBusinessError.UNKNOWN_ERROR.getErrMsg());
//        }
//        return CommonReturnType.create(responseData,"fail");
//
//    }
}
