package top.easyblog.seckill;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/**
 * 启动类
 *
 * @author Administrator
 */
@EnableTransactionManagement
@EnableWebMvc
@SpringBootApplication
@MapperScan(basePackages = "top.easyblog.seckill.model.mapper")
public class SecKillApplication extends SpringBootServletInitializer {

    public static void main(String[] args) {
        SpringApplication.run(SecKillApplication.class, args);
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(SecKillApplication.class);
    }
}
