package com.telegrambox.bot.model;

/** 商品行（products 表）。price 为占位价格，单位 USD。 */
public record Product(
        long id,
        String category,
        String name,
        String description,
        double price,
        String duration,
        boolean active,
        int sortOrder,
        String createdAt) {
}
