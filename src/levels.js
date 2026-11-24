// 会员等级与金额格式化。
// 等级按累计消费算（users.total_spent），门槛在 config 里可配：
//   Bronze 0（固定）· Silver 100（由截图推断：累计消费 $11.502 时距 Silver 还差 $88.498）
//   Gold 500（占位）· Platinum 1000（占位），等用户确认真实阈值后再改。
const config = require('./config');

const LEVELS = [
  { name: 'Bronze', min: 0 },
  { name: 'Silver', min: config.levelSilverMin },
  { name: 'Gold', min: config.levelGoldMin },
  { name: 'Platinum', min: config.levelPlatinumMin },
];

function levelFor(totalSpent) {
  let cur = LEVELS[0];
  for (const l of LEVELS) {
    if (totalSpent >= l.min) cur = l;
  }
  return cur;
}

function nextLevel(totalSpent) {
  for (const l of LEVELS) {
    if (totalSpent < l.min) return l;
  }
  return null; // 已是最高等级
}

// 金额格式化：最多 4 位小数，去掉末尾多余的 0。
// 这样 $8.9983、$11.502、$88.498 都能按截图原样显示。
function fmtMoney(n, maxDp = 4) {
  let s = Number(n || 0).toFixed(maxDp);
  if (s.includes('.')) s = s.replace(/0+$/, '').replace(/\.$/, '');
  return s;
}

function fmtUsd(n) {
  return `$${fmtMoney(n)}`;
}

module.exports = { LEVELS, levelFor, nextLevel, fmtMoney, fmtUsd };
