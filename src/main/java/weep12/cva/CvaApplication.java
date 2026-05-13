package weep12.cva;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * CVA 商城后端应用启动入口。
 *
 * <p>自动装配 Spring Boot、Spring Security、MyBatis、Redis 等组件。
 * {@code @MapperScan} 扫描 mapper 包，将 MyBatis 映射器接口注册为 Spring Bean。</p>
 */
@SpringBootApplication
@MapperScan("weep12.cva.mapper")
public class CvaApplication {

    public static void main(String[] args) {
        SpringApplication.run(CvaApplication.class, args);
    }
}
