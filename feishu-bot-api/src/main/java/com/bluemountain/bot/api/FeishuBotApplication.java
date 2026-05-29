package com.bluemountain.bot.api;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 跨模块也能扫到
 */
@SpringBootApplication(scanBasePackages = "com.bluemountain.bot")
@MapperScan("com.bluemountain.bot.**.mapper")
/**
 * 定时任务开启,审批提醒功能的实现
 */
@EnableScheduling
public class FeishuBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(FeishuBotApplication.class, args);
    }
}
