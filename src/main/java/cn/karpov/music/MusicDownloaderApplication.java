package cn.karpov.music;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Spring Boot 应用入口。
 *
 * <p>{@link ConfigurationPropertiesScan} 负责发现并绑定 {@code music.*}
 * 配置类，避免在配置模块里逐个注册属性 Bean。</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class MusicDownloaderApplication {
    public static void main(String[] args) {
        SpringApplication.run(MusicDownloaderApplication.class, args);
    }
}
