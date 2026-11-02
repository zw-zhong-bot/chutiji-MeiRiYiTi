package com.chutiji.pool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 难度等价判断（不连库）：按键排序后逐字段比对，键序、数值写法都不影响结果。
 */
class PoolDifficultyEquivalenceTest {

    private PoolService newService() {
        return new PoolService(null, null, null, new ObjectMapper(), new PoolProperties());
    }

    @Test
    @DisplayName("{a:1,b:2} 与 {b:2,a:1} 等价")
    void keyOrderIsIrrelevant() {
        PoolService service = newService();
        Map<String, Object> requested = new LinkedHashMap<>();
        requested.put("b", 2);
        requested.put("a", 1);

        assertTrue(service.sameDifficulty("{\"a\":1,\"b\":2}", requested), "{a:1,b:2} 与 {b:2,a:1} 是同一档");
        assertTrue(service.sameDifficulty("{\"b\":2,\"a\":1}", requested), "对照：同序也等价");
    }

    @Test
    @DisplayName("100 与 100.0 等价，且不影响其他字段的严格比对")
    void numberFormIsIrrelevant() {
        PoolService service = newService();
        Map<String, Object> requested = new LinkedHashMap<>();
        requested.put("maxOperand", 100);
        requested.put("allowDivision", true);

        assertTrue(service.sameDifficulty("{\"maxOperand\":100.0,\"allowDivision\":true}", requested));
        assertFalse(service.sameDifficulty("{\"maxOperand\":100,\"allowDivision\":false}", requested),
                "值不同就是不同档");
    }

    @Test
    @DisplayName("字段多一个或少一个都不等价")
    void extraFieldIsNotEquivalent() {
        PoolService service = newService();
        Map<String, Object> requested = new LinkedHashMap<>();
        requested.put("a", 1);

        assertFalse(service.sameDifficulty("{\"a\":1,\"b\":2}", requested), "多出的字段必须让等价判定失败");
        assertTrue(service.sameDifficulty("{\"a\":1}", requested));
        assertEquals(1, requested.size(), "判断过程不得修改入参");
    }

    @Test
    @DisplayName("空难度与空 JSON 等价")
    void emptyDifficulty() {
        PoolService service = newService();
        assertTrue(service.sameDifficulty("{}", Map.of()));
        assertFalse(service.sameDifficulty(null, Map.of()));
        assertFalse(service.sameDifficulty("  ", Map.of()));
    }
}
