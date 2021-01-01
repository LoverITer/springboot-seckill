package top.easyblog.seckill.service;

import top.easyblog.seckill.error.BusinessException;
import top.easyblog.seckill.model.UserModel;

/**
 *
 * @author Huang Xin
 */
public interface UserService {
    /**
     * 通过用户ID获取用户对象的方法
     * @param id
     * @return
     */
    UserModel getUserById(Integer id);


    UserModel getUserByIdInCache(Integer userId);


    void register(UserModel userModel) throws BusinessException;


    /**
    *telphone:用户注册手机
    *password:用户加密后的密码
     */
    UserModel validateLogin(String telphone,String encrptPassword) throws BusinessException, BusinessException;
}
