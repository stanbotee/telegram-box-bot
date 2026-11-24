package com.telegrambox.bot.model;

/** 库存行（stock_items 表）。content 就是一行发货内容：账号密码 / 激活码 / 卡密。 */
public record StockItem(
        long id,
        long productId,
        String content,
        boolean used,
        Long usedByOrderId,
        String createdAt) {
}
