package com.chutiji.review;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * 知识点指纹：module_key + ":" + sha256(难度配置 JSON 按键排序后的文本)。
 *
 * <p>排序是必须的：{@code {a:1,b:2}} 与 {@code {b:2,a:1}} 是同一档难度，
 * 若按原顺序序列化会算出两个 key，同一档难度分裂成多张卡，调度直接乱套。
 */
public final class KnowledgeKey {

    private KnowledgeKey() {
    }

    public static String of(String moduleKey, Map<String, Object> difficulty) {
        return moduleKey + ":" + sha256Hex(canonicalJson(difficulty));
    }

    /** 键序无关、数值写法无关（100 与 100.0 等价）的 JSON 文本。 */
    public static String canonicalJson(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof Map<?, ?> map) {
            writeObject(sb, map);
        } else if (value instanceof Collection<?> collection) {
            sb.append('[');
            boolean first = true;
            for (Object item : collection) {
                if (!first) {
                    sb.append(',');
                }
                write(sb, item);
                first = false;
            }
            sb.append(']');
        } else if (value instanceof Object[] array) {
            write(sb, List.of(array));
        } else if (value instanceof Number number) {
            sb.append(canonicalNumber(number));
        } else if (value instanceof Boolean bool) {
            sb.append(bool);
        } else {
            writeString(sb, value.toString());
        }
    }

    private static void writeObject(StringBuilder sb, Map<?, ?> map) {
        List<String> keys = new ArrayList<>(new TreeSet<>(stringKeys(map)));
        sb.append('{');
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            if (i > 0) {
                sb.append(',');
            }
            writeString(sb, key);
            sb.append(':');
            write(sb, map.get(key));
        }
        sb.append('}');
    }

    private static List<String> stringKeys(Map<?, ?> map) {
        List<String> keys = new ArrayList<>(map.size());
        for (Object key : map.keySet()) {
            keys.add(String.valueOf(key));
        }
        return keys;
    }

    /**
     * Jackson 反序列化会把整数读成 Integer、把带小数点的读成 Double；
     * 100 与 100.0 是同一档难度，必须归一化成同一个文本，否则指纹会漂。
     */
    static String canonicalNumber(Number number) {
        if (number instanceof Double || number instanceof Float) {
            double value = number.doubleValue();
            if (!Double.isFinite(value)) {
                return Double.toString(value);
            }
            if (value == Math.rint(value) && Math.abs(value) < 1.0e15) {
                return Long.toString((long) value);
            }
            return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
        }
        if (number instanceof Integer || number instanceof Long || number instanceof Short
                || number instanceof Byte) {
            return Long.toString(number.longValue());
        }
        return new BigDecimal(number.toString()).stripTrailingZeros().toPlainString();
    }

    private static void writeString(StringBuilder sb, String text) {
        sb.append('"');
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            switch (ch) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        sb.append(String.format("\\u%04x", (int) ch));
                    } else {
                        sb.append(ch);
                    }
                }
            }
        }
        sb.append('"');
    }

    static String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }
}
