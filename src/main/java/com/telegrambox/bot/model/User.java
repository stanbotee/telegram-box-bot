package com.telegrambox.bot.model;

/** 用户行（users 表）。余额与累计消费单位为 USD。 */
public record User(
        long id,
        String username,
        String firstName,
        String language,
        double balance,
        double totalSpent,
        Long referrerId,
        String apiKey,
        boolean notifications,
        String createdAt) {
}
