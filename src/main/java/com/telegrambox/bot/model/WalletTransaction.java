package com.telegrambox.bot.model;

/** 钱包流水行（wallet_transactions 表）。type：topup / purchase / commission / refund。 */
public record WalletTransaction(
        long id,
        long userId,
        String type,
        double amount,
        String currency,
        String ref,
        String createdAt) {
}
