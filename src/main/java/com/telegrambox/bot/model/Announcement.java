package com.telegrambox.bot.model;

/** 公告行（announcements 表）。 */
public record Announcement(
        long id,
        String title,
        String body,
        boolean active,
        String createdAt) {
}
