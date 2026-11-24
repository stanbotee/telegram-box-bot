package com.telegrambox.bot.model;

/** 充值申请行（topups 表）。状态：pending | confirmed | rejected。 */
public record Topup(
        long id,
        long userId,
        double amount,
        String currency,
        String txid,
        String status,
        String createdAt) {
}
