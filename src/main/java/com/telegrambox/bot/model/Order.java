package com.telegrambox.bot.model;

/**
 * 订单行（orders 表）。
 * 状态：pending_payment 待处理 | awaiting_confirm 处理中
 *      | paid 已确认 | delivered 已发货 | rejected 已拒绝 | cancelled 已取消。
 * productName / productDuration 只在联表查询（订单详情）时填充。
 */
public record Order(
        long id,
        long userId,
        long productId,
        int qty,
        double total,
        String status,
        String paymentMethod,
        String paymentRef,
        Long stockItemId,
        String createdAt,
        String updatedAt,
        String productName,
        String productDuration) {

    /** 订单状态常量 */
    public static final String PENDING_PAYMENT = "pending_payment";
    public static final String AWAITING_CONFIRM = "awaiting_confirm";
    public static final String PAID = "paid";
    public static final String DELIVERED = "delivered";
    public static final String REJECTED = "rejected";
    public static final String CANCELLED = "cancelled";
}
