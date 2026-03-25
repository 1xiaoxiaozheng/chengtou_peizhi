package com.SpringbootTZ.FACT;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;
// import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableRetry
 @EnableScheduling // 启用定时任务（已关闭）
public class FactApplication {

    public static void main(String[] args) {
        SpringApplication.run(FactApplication.class, args);
        System.out.println("好读书"); // 打印好读书
    }

//好读书
}
