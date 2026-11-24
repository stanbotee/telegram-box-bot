package com.telegrambox.bot.service;

import com.telegrambox.bot.config.AppConfig;

import java.util.List;

/** 等级推断。Bronze 0；Silver / Gold / Platinum 阈值来自 AppConfig。 */
public final class LevelService {

    public record Level(String name, double min) {
    }

    private final List<Level> levels;

    public LevelService(AppConfig config) {
        this.levels = List.of(
                new Level("Bronze", 0),
                new Level("Silver", config.levelSilverMin),
                new Level("Gold", config.levelGoldMin),
                new Level("Platinum", config.levelPlatinumMin));
    }

    /** 累计消费对应的当前等级。 */
    public Level levelFor(double totalSpent) {
        Level cur = levels.get(0);
        for (Level l : levels) {
            if (totalSpent >= l.min()) {
                cur = l;
            } else {
                break;
            }
        }
        return cur;
    }

    /** 距离下一等级还差多少（null 表示已到最高级）。 */
    public Double remainingToNextLevel(double totalSpent) {
        for (Level l : levels) {
            if (totalSpent < l.min()) {
                return l.min() - totalSpent;
            }
        }
        return null;
    }

    /** 下一等级名称（null 表示已到最高级）。 */
    public String nextLevelName(double totalSpent) {
        for (Level l : levels) {
            if (totalSpent < l.min()) {
                return l.name();
            }
        }
        return null;
    }

    /** 当前等级在整个等级列表中的位置（0 起）。 */
    public int indexOf(String levelName) {
        for (int i = 0; i < levels.size(); i++) {
            if (levels.get(i).name().equals(levelName)) {
                return i;
            }
        }
        return 0;
    }
}
