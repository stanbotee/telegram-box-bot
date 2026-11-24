package com.telegrambox.bot.service;

import com.telegrambox.bot.config.AppConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 等级推断：阈值 Bronze 0 / Silver 100 / Gold 500 / Platinum 1000。 */
class LevelServiceTest {

    private final LevelService levels = new LevelService(AppConfig.load());

    @Test
    void screenshotCaseIsBronze() {
        // 截图：累计消费 $11.502，距离 Silver 还差 $88.498
        assertEquals("Bronze", levels.levelFor(11.502).name());
        assertEquals(88.498, levels.remainingToNextLevel(11.502), 1e-9);
        assertEquals("Silver", levels.nextLevelName(11.502));
    }

    @Test
    void thresholds() {
        assertEquals("Bronze", levels.levelFor(0).name());
        assertEquals("Bronze", levels.levelFor(99.99).name());
        assertEquals("Silver", levels.levelFor(100).name());
        assertEquals("Silver", levels.levelFor(499.99).name());
        assertEquals("Gold", levels.levelFor(500).name());
        assertEquals("Gold", levels.levelFor(999.99).name());
        assertEquals("Platinum", levels.levelFor(1000).name());
        assertEquals("Platinum", levels.levelFor(100000).name());
    }

    @Test
    void maxLevelHasNoNext() {
        assertNull(levels.remainingToNextLevel(1000));
        assertNull(levels.nextLevelName(1000));
        assertEquals("Platinum", levels.levelFor(1000).name());
    }

    @Test
    void remainingAtSilver() {
        assertEquals(400.0, levels.remainingToNextLevel(100), 1e-9);
        assertEquals("Gold", levels.nextLevelName(100));
    }
}
