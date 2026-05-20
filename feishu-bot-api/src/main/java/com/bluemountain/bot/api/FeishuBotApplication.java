package com.bluemountain.bot.api;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.bluemountain.bot")
@MapperScan("com.bluemountain.bot.**.mapper")
@EnableScheduling
public class FeishuBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(FeishuBotApplication.class, args);
    }
}
