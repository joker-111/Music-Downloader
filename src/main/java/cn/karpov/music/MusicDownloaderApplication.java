package cn.karpov.music;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MusicDownloaderApplication {
    public static void main(String[] args) {
        SpringApplication.run(MusicDownloaderApplication.class, args);
    }
}
