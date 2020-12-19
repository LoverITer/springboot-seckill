package top.easyblog.seckill.api.utils;

import javax.servlet.http.HttpServletRequest;

/**
 * @author ：huangxin
 * @modified ：
 * @since ：2020/12/11 22:18
 */
public class NetWorkUtils {


    /**
     * 获取服务器的根路径，比如：http://www.baidu,com/
     *
     * @return
     */
    public StringBuffer getHostRootPath(HttpServletRequest request){
        //服务器根路径
        StringBuffer domain=new StringBuffer();
        domain.append(request.getScheme()).append("://")
                .append(request.getServerName()).append(":")
                .append(request.getServerPort())
                .append(request.getContextPath()).append("/");
        return domain;
    }

}
