package com.telegrambox.bot.model;

/** 返佣记录行（referrals 表）。 */
public record Referral(
        long id,
        long referrerId,
        long referredId,
        long orderId,
        double commission,
        String createdAt) {
}
