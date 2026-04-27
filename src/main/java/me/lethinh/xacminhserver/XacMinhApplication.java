package me.lethinh.xacminhserver;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.ApplicationPidFileWriter;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@SpringBootApplication
public class XacMinhApplication implements ApplicationRunner, WebMvcConfigurer {

    public static String DATABASE_PATH;
    private static final Logger LOGGER = LogManager.getLogger("XacMinh");

    /* ApplicationRunner */
    @Override
    public void run(ApplicationArguments args) {
        // Utils.checkLicense();
        LOGGER.info("XacMinh Server - By Nesfan");
        LOGGER.info("XacMinh Server da duoc mo thanh cong!");

        String serverPath = args.getOptionValues("serverPath").get(0);
        DATABASE_PATH = Paths.get(serverPath, "plugins", "XacMinh", "xacminh.db").toString();

        java.io.File dbDir = new java.io.File(DATABASE_PATH).getParentFile();
        if (!dbDir.exists()) {
            dbDir.mkdirs();
        }
    }

    /* WebMvcConfigurer */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Utils.checkLicense();
        registry.addInterceptor(new RateLimitInterceptor());
    }

    /* Main */
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(XacMinhApplication.class);
        application.addListeners(new ApplicationPidFileWriter("./pid.txt"),
                (ApplicationListener<ContextClosedEvent>) event -> LOGGER.info("Xac Minh Server da thoat!"));
        ConfigurableApplicationContext ctx = application.run(args);
        Runtime.getRuntime().addShutdownHook(new Thread(ctx::close));
        // Utils.checkLicense(ctx);
    }

}
