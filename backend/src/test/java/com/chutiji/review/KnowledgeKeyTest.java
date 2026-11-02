package com.chutiji.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** knowledge_key = module_key + ":" + sha256(按键排序后的难度配置 JSON)。 */
class KnowledgeKeyTest {

    @Test
    @DisplayName("键序无关：{a:1,b:2} 与 {b:2,a:1} 生成同一个 key")
    void keyOrderDoesNotMatter() {
        Map<String, Object> ab = new LinkedHashMap<>();
        ab.put("a", 1);
        ab.put("b", 2);

        Map<String, Object> ba = new LinkedHashMap<>();
        ba.put("b", 2);
        ba.put("a", 1);

        String keyAb = KnowledgeKey.of("arithmetic", ab);
        String keyBa = KnowledgeKey.of("arithmetic", ba);

        System.out.println("[knowledge_key] {a:1,b:2} -> " + keyAb);
        System.out.println("[knowledge_key] {b:2,a:1} -> " + keyBa);

        assertEquals(keyAb, keyBa, "键序不同必须算出同一个 key，否则同一档难度会分裂成多张卡");
    }

    @Test
    @DisplayName("真实难度轴键序无关：四则口算的四个轴换顺序仍是同一张卡")
    void realAxisOrderDoesNotMatter() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("maxOperand", 100);
        first.put("operatorCount", 2);
        first.put("allowParentheses", false);
        first.put("allowDivision", true);

        Map<String, Object> second = new LinkedHashMap<>();
        second.put("allowDivision", true);
        second.put("operatorCount", 2);
        second.put("allowParentheses", false);
        second.put("maxOperand", 100);

        assertEquals(KnowledgeKey.of("arithmetic", first), KnowledgeKey.of("arithmetic", second));
        System.out.println("[knowledge_key] arithmetic 四轴乱序 -> " + KnowledgeKey.of("arithmetic", first));
    }

    @Test
    @DisplayName("不同模块 / 不同难度必须算出不同 key")
    void distinctConfigsProduceDistinctKeys() {
        Map<String, Object> a = Map.of("maxOperand", 100);
        Map<String, Object> b = Map.of("maxOperand", 200);

        assertNotEquals(KnowledgeKey.of("arithmetic", a), KnowledgeKey.of("arithmetic", b));
        assertNotEquals(KnowledgeKey.of("arithmetic", a), KnowledgeKey.of("algebra", a));
    }

    @Test
    @DisplayName("key 形如 moduleKey:64 位 sha256，长度不超列宽 128")
    void keyFormatFitsColumn() {
        String key = KnowledgeKey.of("arithmetic", Map.of("maxOperand", 100));
        assertTrue(key.startsWith("arithmetic:"), "必须以 module_key 开头");
        assertEquals("arithmetic:".length() + 64, key.length(), "sha256 十六进制固定 64 位");
        assertTrue(key.length() <= 128, "不得超出 t_review_card.knowledge_key 的 VARCHAR(128)");
    }

    @Test
    @DisplayName("JSON 反序列化把 100 读成 Integer、100.0 读成 Double 时仍是同一个 key")
    void numberTypeDoesNotSplitKey() {
        Map<String, Object> intValue = Map.of("maxOperand", 100);
        Map<String, Object> doubleValue = Map.of("maxOperand", 100.0);
        String keyInt = KnowledgeKey.of("arithmetic", intValue);
        String keyDouble = KnowledgeKey.of("arithmetic", doubleValue);
        System.out.println("[knowledge_key] 100   -> " + keyInt);
        System.out.println("[knowledge_key] 100.0 -> " + keyDouble);
        assertEquals(keyInt, keyDouble, "100 与 100.0 是同一档难度，不能算出两张卡");
    }

    @Test
    @DisplayName("canonicalJson 按键名升序输出")
    void canonicalJsonSortsKeys() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("b", 2);
        map.put("a", 1);
        map.put("c", true);
        assertEquals("{\"a\":1,\"b\":2,\"c\":true}", KnowledgeKey.canonicalJson(map));
    }

    @Test
    @DisplayName("空难度配置也能生成稳定 key")
    void emptyDifficultyIsStable() {
        assertEquals(KnowledgeKey.of("sudoku", Map.of()), KnowledgeKey.of("sudoku", Map.of()));
        assertTrue(KnowledgeKey.of("sudoku", Map.of()).startsWith("sudoku:"));
    }
}
