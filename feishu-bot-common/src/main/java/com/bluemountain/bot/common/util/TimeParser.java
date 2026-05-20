package com.bluemountain.bot.common.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 时间解析工具 — 把自然语言时间转成 LocalDateTime
 */
public class TimeParser {

    /** 模式1：2026-05-20 15:00 */
    private static final Pattern P_STANDARD = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2})\\s+(\\d{1,2}:\\d{2})\\s+(.*)");

    /** 模式2：5月20日 15:00 */
    private static final Pattern P_CHINESE_DATE = Pattern.compile(
            "^(\\d{1,2})月(\\d{1,2})日\\s+(\\d{1,2}:\\d{2})\\s+(.*)");

    /** 模式3：明天/后天/今天 + 时间 + 标题（空格可选） */
    private static final Pattern P_RELATIVE_DAY = Pattern.compile(
            "^(明天|后天|今天)\\s*(\\S+)\\s+(.*)");

    /** 模式4：下午3点 / 上午10点 */
    private static final Pattern P_CHINESE_HOUR = Pattern.compile(
            "^(上午|下午)(\\d{1,2})点\\s+(.*)");

    /** 模式5：纯时间 15:00 */
    private static final Pattern P_TIME_ONLY = Pattern.compile(
            "^(\\d{1,2}:\\d{2})\\s+(.*)");

    /**
     * 解析时间，返回 [LocalDateTime, 标题]
     * 失败返回 null
     */
    public static ParseResult parse(String text) {
        if (text == null || text.isBlank()) return null;

        text = text.trim();

        // ① 标准格式 2026-05-20 15:00 标题
        ParseResult r = tryStandard(text);
        if (r != null) return r;

        // ② 中文月日 5月20日 15:00 标题
        r = tryChineseDate(text);
        if (r != null) return r;

        // ③ 明天/后天/今天 时间 标题
        r = tryRelativeDay(text);
        if (r != null) return r;

        // ④ 下午3点 标题
        r = tryChineseHour(text);
        if (r != null) return r;

        // ⑤ 纯时间 15:00 标题（默认今天）
        r = tryTimeOnly(text);
        if (r != null) return r;

        return null;
    }

    private static ParseResult tryStandard(String text) {
        Matcher m = P_STANDARD.matcher(text);
        if (!m.find()) return null;
        LocalDateTime time = LocalDateTime.parse(m.group(1) + " " + m.group(2),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        return new ParseResult(time, m.group(3));
    }

    private static ParseResult tryChineseDate(String text) {
        Matcher m = P_CHINESE_DATE.matcher(text);
        if (!m.find()) return null;
        int month = Integer.parseInt(m.group(1));
        int day = Integer.parseInt(m.group(2));
        LocalDate date = LocalDate.of(Year.now().getValue(), month, day);
        LocalTime time = LocalTime.parse(m.group(3));
        return new ParseResult(LocalDateTime.of(date, time), m.group(4));
    }

    private static ParseResult tryRelativeDay(String text) {
        Matcher m = P_RELATIVE_DAY.matcher(text);
        if (!m.find()) return null;
        String dayWord = m.group(1);
        String timeStr = m.group(2);
        String title = m.group(3);

        LocalDate date = LocalDate.now();
        if ("明天".equals(dayWord)) date = date.plusDays(1);
        else if ("后天".equals(dayWord)) date = date.plusDays(2);
        // "今天" 不变

        LocalTime time = parseTime(timeStr);
        if (time == null) return null;

        return new ParseResult(LocalDateTime.of(date, time), title);
    }

    private static ParseResult tryChineseHour(String text) {
        Matcher m = P_CHINESE_HOUR.matcher(text);
        if (!m.find()) return null;
        String ampm = m.group(1);
        int hour = Integer.parseInt(m.group(2));
        String title = m.group(3);

        if ("下午".equals(ampm) && hour != 12) hour += 12;
        if ("上午".equals(ampm) && hour == 12) hour = 0;

        return new ParseResult(LocalDateTime.of(LocalDate.now(), LocalTime.of(hour, 0)), title);
    }

    private static ParseResult tryTimeOnly(String text) {
        Matcher m = P_TIME_ONLY.matcher(text);
        if (!m.find()) return null;
        return new ParseResult(
                LocalDateTime.of(LocalDate.now(), LocalTime.parse(m.group(1))),
                m.group(2));
    }

    /** 解析 "15:00" 或 "下午3点" */
    /** 中文数字 → 阿拉伯数字 */
    private static final java.util.Map<String, Integer> CN_NUM = java.util.Map.ofEntries(
            java.util.Map.entry("一",1), java.util.Map.entry("二",2), java.util.Map.entry("三",3),
            java.util.Map.entry("四",4), java.util.Map.entry("五",5), java.util.Map.entry("六",6),
            java.util.Map.entry("七",7), java.util.Map.entry("八",8), java.util.Map.entry("九",9),
            java.util.Map.entry("十",10), java.util.Map.entry("十一",11), java.util.Map.entry("十二",12),
            java.util.Map.entry("两",2)
    );

    private static LocalTime parseTime(String s) {
        try {
            return LocalTime.parse(s);
        } catch (Exception e) {
            // 不是 HH:mm 格式，试中文
        }
        // 匹配：下午3点 / 上午10点 / 下午三点 / 下午三点钟
        Matcher m = Pattern.compile("(上午|下午)([一二两三四五六七八九十\\d]{1,3})点(?:钟)?").matcher(s);
        if (m.find()) {
            String hourStr = m.group(2);
            int hour;
            if (Character.isDigit(hourStr.charAt(0))) {
                hour = Integer.parseInt(hourStr);
            } else {
                hour = CN_NUM.getOrDefault(hourStr, -1);
                if (hour == -1) return null;
            }
            if ("下午".equals(m.group(1)) && hour != 12) hour += 12;
            if ("上午".equals(m.group(1)) && hour == 12) hour = 0;
            return LocalTime.of(hour, 0);
        }
        return null;
    }

    public static class ParseResult {
        public final LocalDateTime time;
        public final String title;

        ParseResult(LocalDateTime time, String title) {
            this.time = time;
            this.title = title;
        }
    }
}
