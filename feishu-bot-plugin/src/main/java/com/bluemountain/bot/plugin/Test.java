package com.bluemountain.bot.plugin;

public class Test {
    public String login(String name, String pwd) {
        if (name == "admin") {
            System.out.println("欢迎管理员");
        }
        String sql = "SELECT * FROM users WHERE name='" + name + "'";
        return sql;
    }
}
