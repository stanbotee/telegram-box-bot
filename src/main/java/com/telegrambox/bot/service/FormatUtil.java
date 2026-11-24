package com.telegrambox.bot.service;

import java.math.BigDecimal;

/**
 * 金额格式化：与 Node 版一致，余额 / 累计消费最多保留 4 位小数，末尾 0 去掉
 * （如 8.9983、11.502、0、88.498）。
 */
public final class FormatUtil {

    private FormatUtil() {
    }

    /** 金额转 4 位小数内、去尾零的字符串。 */
    public static String fmtMoney(double amount) {
        BigDecimal bd = BigDecimal.valueOf(amount)
                .setScale(4, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros();
        return bd.toPlainString();
    }

    /** 金额格式化为 USD 字符串，如 $8.9983。 */
    public static String fmtUsd(double amount) {
        return "$" + fmtMoney(amount);
    }

    /** 返佣金额：保留 2 位小数（四舍五入），避免分以下的小数残留。 */
    public static double round2(double amount) {
        return BigDecimal.valueOf(amount)
                .setScale(2, java.math.RoundingMode.HALF_UP)
                .doubleValue();
    }

    /** 返佣金额 = 被推荐人订单实付 × 比例，保留 2 位小数。 */
    public static double commissionFor(double orderTotal, double rate) {
        return round2(orderTotal * rate);
    }
}
