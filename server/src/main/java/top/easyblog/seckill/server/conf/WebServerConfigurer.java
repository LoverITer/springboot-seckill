package top.easyblog.seckill.server.conf;

import org.apache.coyote.http11.Http11NioProtocol;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.ConfigurableWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Configuration;

/**
 * 定制化内嵌tomcat配置
 *
 * @author ：huangxin
 * @modified ：
 * @since ：2020/12/15 22:12
 */
@Configuration
public class WebServerConfigurer implements WebServerFactoryCustomizer<ConfigurableWebServerFactory> {

    /**
     * 定制化Tomcat的Connector
     * @param factory
     */
    @Override
    public void customize(ConfigurableWebServerFactory factory) {
        ((TomcatServletWebServerFactory)factory).addConnectorCustomizers(connector -> {
            Http11NioProtocol protocolHandler = (Http11NioProtocol)connector.getProtocolHandler();

            //定制化keepalivetimome
            protocolHandler.setKeepAliveTimeout(30000);
            //当客户端发送超过10000个请求则自动断开请求
            protocolHandler.setMaxKeepAliveRequests(10000);
        });
    }
}
