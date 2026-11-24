// 通用小工具
async function safeEdit(ctx, text, extra = {}) {
  try {
    await ctx.editMessageText(text, extra);
  } catch (e) {
    // 消息已删 / 内容未变 / 太旧时改发新消息
    await ctx.reply(text, extra);
  }
}

function fmtPrice(n) {
  return `$${Number(n).toFixed(2)}`;
}

function isAdmin(ctx, config) {
  return ctx.from && config.adminIds.includes(ctx.from.id);
}

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

// 给所有管理员发一条通知（单个失败不影响其他人）
async function notifyAdmins(bot, config, text) {
  for (const adminId of config.adminIds) {
    try {
      await bot.telegram.sendMessage(adminId, text);
    } catch (e) {
      console.error(`通知管理员 ${adminId} 失败:`, e.message);
    }
  }
}

module.exports = { safeEdit, fmtPrice, isAdmin, sleep, notifyAdmins };
