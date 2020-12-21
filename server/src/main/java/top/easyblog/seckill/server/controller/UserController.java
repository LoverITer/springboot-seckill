package top.easyblog.seckill.server.controller;

import com.alibaba.fastjson.JSON;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import sun.misc.BASE64Encoder;
import top.easyblog.seckill.api.error.AppResponseCode;
import top.easyblog.seckill.api.error.BusinessException;
import top.easyblog.seckill.api.error.EmBusinessError;
import top.easyblog.seckill.api.response.CommonResponse;
import top.easyblog.seckill.api.service.UserService;
import top.easyblog.seckill.model.UserModel;
import top.easyblog.seckill.model.vo.UserVO;
import top.easyblog.seckill.server.service.RedisService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;
import java.util.UUID;

/**
 * Created by hzllb on 2018/11/11.
 */
@Controller("user")
@RequestMapping("/user")
@CrossOrigin(allowCredentials = "true", allowedHeaders = "*")
public class UserController extends BaseController {

    @Autowired
    private UserService userService;

    @Autowired
    private HttpServletRequest httpServletRequest;

    @Autowired
    private RedisService redisService;

    private static final String OTP_CODE_MAP_KEY="opt_code_map";

    private static final String OTP_CODE_PREFIX="opt_code_";


    //用户注册接口
    @RequestMapping(value = "/register", method = {RequestMethod.POST}, consumes = {CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonResponse register(@RequestParam(name = "telphone") String telphone,
                                   @RequestParam(name = "otpCode") String otpCode,
                                   @RequestParam(name = "name") String name,
                                   @RequestParam(name = "gender") Integer gender,
                                   @RequestParam(name = "age") Integer age,
                                   @RequestParam(name = "password") String password) throws BusinessException, UnsupportedEncodingException, NoSuchAlgorithmException {
        try{
            //验证手机号和对应的otpcode相符合
            String OtpCode = (String)redisService.hget(OTP_CODE_MAP_KEY, OTP_CODE_PREFIX + telphone, RedisService.RedisDataBaseSelector.DB_0);

            if (!StringUtils.isEmpty(OtpCode)&&!OtpCode.equals(otpCode)) {
                throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "短信验证码不符合");
            }
            //用户的注册流程
            UserModel userModel = new UserModel();
            userModel.setName(name);
            userModel.setGender(new Byte(String.valueOf(gender.intValue())));
            userModel.setAge(age);
            userModel.setTelphone(telphone);
            userModel.setRegisterMode("byphone");
            userModel.setEncrptPassword(this.EncodeByMd5(password));
            userService.register(userModel);
            return CommonResponse.create(AppResponseCode.SUCCESS);
        }finally {
            redisService.hdel(RedisService.RedisDataBaseSelector.DB_0,OTP_CODE_MAP_KEY, OTP_CODE_PREFIX + telphone);
        }
    }

    public String EncodeByMd5(String str) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        //确定计算方法
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        BASE64Encoder base64en = new BASE64Encoder();
        //加密字符串
        String newstr = base64en.encode(md5.digest(str.getBytes("utf-8")));
        return newstr;
    }


    //用户获取otp短信接口
    @RequestMapping(value = "/getotp", method = {RequestMethod.POST}, consumes = {CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonResponse getOtp(@RequestParam(name = "telphone") String telphone) {
        //需要按照一定的规则生成OTP验证码
        Random random = new Random();
        int randomInt = random.nextInt(99999);
        randomInt += 10000;
        String otpCode = String.valueOf(randomInt);
        //将验证码尺寸到Redis中，用户注册的时候验证手机号的有效性
        redisService.hset(OTP_CODE_MAP_KEY,OTP_CODE_PREFIX+telphone,otpCode,300, RedisService.RedisDataBaseSelector.DB_0);

        //短信服务：将OTP验证码通过短信通道发送给用户,省略
        System.out.println("telphone = " + telphone + " & otpCode = " + otpCode);

        return CommonResponse.create(AppResponseCode.SUCCESS);
    }


    @RequestMapping("/get")
    @ResponseBody
    public CommonResponse<UserVO> getUser(@RequestParam(name = "id") Integer id) throws BusinessException {
        //调用service服务获取对应id的用户对象并返回给前端
        UserModel userModel = userService.getUserById(id);

        //若获取的对应用户信息不存在
        if (userModel == null) {
            throw new BusinessException(EmBusinessError.USER_NOT_EXIST);
        }

        //讲核心领域模型用户对象转化为可供UI使用的viewobject
        UserVO userVO = convertFromModel(userModel);


        //返回通用对象
        return CommonResponse.create(AppResponseCode.SUCCESS, userVO);
    }

    private UserVO convertFromModel(UserModel userModel) {
        if (userModel == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(userModel, userVO);
        return userVO;
    }



    /**
     * 用户登录接口，实现了分布式session
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
        String userLoginToken = String.format("%d%s", userModel.getId(), UUID.randomUUID().toString().replaceAll("-", ""));

        Boolean res = redisService.setnx(userLoginToken, JSON.toJSONString(userModel), RedisService.RedisDataBaseSelector.DB_0);
        if (res == null) {
            return CommonResponse.create(AppResponseCode.USER_LOGIN_FAILE, "服务异常，请稍后重试！");
        } else if (!res) {
            return CommonResponse.create(AppResponseCode.USER_LOGIN_REPEAT, "您已登录,请不要重复登录");
        }
        redisService.expire(userLoginToken, MAX_USER_LOGIN_STATUS_KEEP_TIME, RedisService.RedisDataBaseSelector.DB_0);

        return CommonResponse.create(AppResponseCode.USER_LOGIN_SUCCESS, userLoginToken);
    }


}
