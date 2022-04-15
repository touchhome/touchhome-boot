package org.touchhome.app;

import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.touchhome.app.config.TouchHomeConfig;
import org.touchhome.bundle.api.hquery.EnableHQuery;

@Log4j2
@EnableHQuery(scanBaseClassesPackage = "org.touchhome")
@SpringBootApplication(exclude = {
        ErrorMvcAutoConfiguration.class})
public class TouchHomeBootApplication implements WebMvcConfigurer {

    public static void main(String[] args) {
        new SpringApplicationBuilder(TouchHomeConfig.class).run(args);
    }
}
