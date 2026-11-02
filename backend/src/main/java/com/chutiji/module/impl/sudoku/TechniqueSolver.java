package com.chutiji.module.impl.sudoku;

/**
 * 技巧求解器：只用人类可说明的技巧逐步推进，记录用到的最高级技巧。
 * 卡住（需要猜测）即视为超出 EXPERT，返回 solved=false。
 *
 * 等级归属：
 * EASY   唯一候选 Naked Single、隐性唯一 Hidden Single
 * MEDIUM 数对 Naked/Hidden Pair
 * HARD   三数组、区块删减 Pointing/Claiming
 * EXPERT X-Wing、XY-Wing
 *
 * 技巧按等级从低到高试，先能推进的就用，所以“不超过 maxLevel 能否解出”是确定的。
 */
final class TechniqueSolver {

    private static final int FULL = 0x1FF;

    private static final int[] ROW = new int[81];
    private static final int[] COL = new int[81];
    private static final int[] BOX = new int[81];
    private static final int[][] UNITS = new int[27][9];

    static {
        for (int cell = 0; cell < 81; cell++) {
            ROW[cell] = cell / 9;
            COL[cell] = cell % 9;
            BOX[cell] = (ROW[cell] / 3) * 3 + COL[cell] / 3;
        }
        for (int unit = 0; unit < 27; unit++) {
            for (int slot = 0; slot < 9; slot++) {
                UNITS[unit][slot] = unitCell(unit, slot);
            }
        }
    }

    private TechniqueSolver() {
    }

    private static int unitCell(int unit, int slot) {
        if (unit < 9) {
            return unit * 9 + slot;
        }
        if (unit < 18) {
            int column = unit - 9;
            return slot * 9 + column;
        }
        int box = unit - 18;
        return (box / 3) * 27 + (box % 3) * 3 + (slot / 3) * 9 + slot % 3;
    }

    private static boolean inUnit(int cell, int unit) {
        if (unit < 9) {
            return ROW[cell] == unit;
        }
        if (unit < 18) {
            return COL[cell] == unit - 9;
        }
        return BOX[cell] == unit - 18;
    }

    /** 求解结果：solved=false 表示给定等级的技巧不够用；level 是实际用到的最高等级。 */
    record Result(boolean solved, TechniqueLevel level) {
    }

    /** 以不超过 maxLevel 的技巧求解。 */
    static Result solve(int[] puzzle, TechniqueLevel maxLevel) {
        int[] grid = puzzle.clone();
        int[] cand = buildCandidates(grid);
        if (cand == null) {
            return new Result(false, null);
        }

        TechniqueLevel hardest = null;
        while (true) {
            int empty = firstEmpty(grid);
            if (empty < 0) {
                return new Result(true, hardest == null ? TechniqueLevel.EASY : hardest);
            }
            TechniqueLevel used = stepOnce(grid, cand, maxLevel);
            if (used == null) {
                return new Result(false, null);
            }
            if (hardest == null || used.ordinal() > hardest.ordinal()) {
                hardest = used;
            }
        }
    }

    /** 构造候选掩码，题目本身矛盾返回 null。 */
    private static int[] buildCandidates(int[] grid) {
        for (int unit = 0; unit < 27; unit++) {
            int seen = 0;
            for (int slot = 0; slot < 9; slot++) {
                int digit = grid[UNITS[unit][slot]];
                if (digit == 0) {
                    continue;
                }
                int bit = 1 << (digit - 1);
                if ((seen & bit) != 0) {
                    return null;
                }
                seen |= bit;
            }
        }
        int[] cand = new int[81];
        for (int cell = 0; cell < 81; cell++) {
            if (grid[cell] != 0) {
                continue;
            }
            int mask = FULL;
            for (int peer : peers(cell)) {
                if (grid[peer] != 0) {
                    mask &= ~(1 << (grid[peer] - 1));
                }
            }
            if (mask == 0) {
                return null;
            }
            cand[cell] = mask;
        }
        return cand;
    }

    /** 20 个邻格：同行 8 个、同列 8 个、同宫 4 个（去重后正好 20）。 */
    private static int[] peers(int cell) {
        int[] result = new int[20];
        int cursor = 0;
        for (int slot = 0; slot < 9; slot++) {
            int rowCell = ROW[cell] * 9 + slot;
            if (rowCell != cell) {
                result[cursor++] = rowCell;
            }
        }
        for (int slot = 0; slot < 9; slot++) {
            if (slot == ROW[cell]) {
                continue;
            }
            result[cursor++] = slot * 9 + COL[cell];
        }
        int startRow = (ROW[cell] / 3) * 3;
        int startCol = (COL[cell] / 3) * 3;
        for (int r = startRow; r < startRow + 3; r++) {
            for (int c = startCol; c < startCol + 3; c++) {
                if (r != ROW[cell] && c != COL[cell]) {
                    result[cursor++] = r * 9 + c;
                }
            }
        }
        if (cursor != 20) {
            throw new IllegalStateException("邻格数量异常: " + cursor);
        }
        return result;
    }

    private static int firstEmpty(int[] grid) {
        for (int cell = 0; cell < 81; cell++) {
            if (grid[cell] == 0) {
                return cell;
            }
        }
        return -1;
    }

    private static TechniqueLevel stepOnce(int[] grid, int[] cand, TechniqueLevel maxLevel) {
        if (nakedSingle(grid, cand) || hiddenSingle(grid, cand)) {
            return TechniqueLevel.EASY;
        }
        if (maxLevel.ordinal() < TechniqueLevel.MEDIUM.ordinal()) {
            return null;
        }
        if (nakedPair(cand) || hiddenPair(cand)) {
            return TechniqueLevel.MEDIUM;
        }
        if (maxLevel.ordinal() < TechniqueLevel.HARD.ordinal()) {
            return null;
        }
        if (nakedTriple(cand) || hiddenTriple(cand) || pointing(cand) || claiming(cand)) {
            return TechniqueLevel.HARD;
        }
        if (maxLevel.ordinal() < TechniqueLevel.EXPERT.ordinal()) {
            return null;
        }
        if (xWing(cand) || xyWing(cand)) {
            return TechniqueLevel.EXPERT;
        }
        return null;
    }

    private static boolean place(int[] grid, int[] cand, int cell, int digit) {
        int bit = 1 << (digit - 1);
        if (cand[cell] == 0 || (cand[cell] & bit) == 0) {
            return false;
        }
        grid[cell] = digit;
        cand[cell] = 0;
        for (int peer : peers(cell)) {
            if (grid[peer] == 0) {
                cand[peer] &= ~bit;
            }
        }
        return true;
    }

    private static boolean nakedSingle(int[] grid, int[] cand) {
        for (int cell = 0; cell < 81; cell++) {
            if (grid[cell] == 0 && Integer.bitCount(cand[cell]) == 1) {
                return place(grid, cand, cell, Integer.numberOfTrailingZeros(cand[cell]) + 1);
            }
        }
        return false;
    }

    private static boolean hiddenSingle(int[] grid, int[] cand) {
        for (int unit = 0; unit < 27; unit++) {
            for (int digit = 1; digit <= 9; digit++) {
                int bit = 1 << (digit - 1);
                int target = -1;
                int count = 0;
                for (int slot = 0; slot < 9; slot++) {
                    int cell = UNITS[unit][slot];
                    if (grid[cell] == 0 && (cand[cell] & bit) != 0) {
                        target = cell;
                        if (++count > 1) {
                            break;
                        }
                    }
                }
                if (count == 1) {
                    return place(grid, cand, target, digit);
                }
            }
        }
        return false;
    }

    private static boolean nakedPair(int[] cand) {
        for (int unit = 0; unit < 27; unit++) {
            int[] cells = UNITS[unit];
            for (int a = 0; a < 9; a++) {
                int maskA = cand[cells[a]];
                if (maskA == 0 || Integer.bitCount(maskA) != 2) {
                    continue;
                }
                for (int b = a + 1; b < 9; b++) {
                    if (cand[cells[b]] != maskA) {
                        continue;
                    }
                    if (eliminateOthers(cand, unit, new int[]{cells[a], cells[b]}, maskA)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean hiddenPair(int[] cand) {
        for (int unit = 0; unit < 27; unit++) {
            for (int d1 = 1; d1 <= 9; d1++) {
                int[] spots1 = spots(cand, unit, 1 << (d1 - 1));
                if (spots1.length != 2) {
                    continue;
                }
                for (int d2 = d1 + 1; d2 <= 9; d2++) {
                    int[] spots2 = spots(cand, unit, 1 << (d2 - 1));
                    if (spots2.length != 2 || spots1[0] != spots2[0] || spots1[1] != spots2[1]) {
                        continue;
                    }
                    int mask = (1 << (d1 - 1)) | (1 << (d2 - 1));
                    boolean changed = false;
                    for (int cell : spots1) {
                        if (cand[cell] != mask) {
                            cand[cell] = mask;
                            changed = true;
                        }
                    }
                    if (changed) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean nakedTriple(int[] cand) {
        for (int unit = 0; unit < 27; unit++) {
            int[] cells = UNITS[unit];
            for (int a = 0; a < 9; a++) {
                if (cand[cells[a]] == 0) {
                    continue;
                }
                for (int b = a + 1; b < 9; b++) {
                    if (cand[cells[b]] == 0) {
                        continue;
                    }
                    for (int c = b + 1; c < 9; c++) {
                        if (cand[cells[c]] == 0) {
                            continue;
                        }
                        int mask = cand[cells[a]] | cand[cells[b]] | cand[cells[c]];
                        if (Integer.bitCount(mask) != 3) {
                            continue;
                        }
                        if (eliminateOthers(cand, unit, new int[]{cells[a], cells[b], cells[c]}, mask)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static boolean hiddenTriple(int[] cand) {
        for (int unit = 0; unit < 27; unit++) {
            int[][] spots = new int[10][];
            for (int digit = 1; digit <= 9; digit++) {
                spots[digit] = spots(cand, unit, 1 << (digit - 1));
            }
            for (int d1 = 1; d1 <= 9; d1++) {
                if (spots[d1].length < 2 || spots[d1].length > 3) {
                    continue;
                }
                for (int d2 = d1 + 1; d2 <= 9; d2++) {
                    if (spots[d2].length < 2 || spots[d2].length > 3) {
                        continue;
                    }
                    for (int d3 = d2 + 1; d3 <= 9; d3++) {
                        if (spots[d3].length < 2 || spots[d3].length > 3) {
                            continue;
                        }
                        int[] cells = union(spots[d1], spots[d2], spots[d3]);
                        if (cells.length != 3) {
                            continue;
                        }
                        int mask = (1 << (d1 - 1)) | (1 << (d2 - 1)) | (1 << (d3 - 1));
                        boolean changed = false;
                        for (int cell : cells) {
                            if ((cand[cell] & ~mask) != 0) {
                                cand[cell] &= mask;
                                changed = true;
                            }
                        }
                        if (changed) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static boolean pointing(int[] cand) {
        for (int box = 18; box < 27; box++) {
            for (int digit = 1; digit <= 9; digit++) {
                int bit = 1 << (digit - 1);
                int[] cells = spots(cand, box, bit);
                if (cells.length < 2) {
                    continue;
                }
                if (sameRow(cells) && eliminateOutside(cand, ROW[cells[0]], box, bit)) {
                    return true;
                }
                if (sameCol(cells) && eliminateOutside(cand, 9 + COL[cells[0]], box, bit)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean claiming(int[] cand) {
        for (int unit = 0; unit < 18; unit++) {
            for (int digit = 1; digit <= 9; digit++) {
                int bit = 1 << (digit - 1);
                int[] cells = spots(cand, unit, bit);
                if (cells.length < 2) {
                    continue;
                }
                int box = BOX[cells[0]];
                boolean sameBox = true;
                for (int cell : cells) {
                    if (BOX[cell] != box) {
                        sameBox = false;
                        break;
                    }
                }
                if (sameBox && eliminateOutside(cand, 18 + box, unit, bit)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean xWing(int[] cand) {
        for (int digit = 1; digit <= 9; digit++) {
            int bit = 1 << (digit - 1);
            if (xWingDirection(cand, bit, true) || xWingDirection(cand, bit, false)) {
                return true;
            }
        }
        return false;
    }

    /** 两条平行线上该数字恰好落在同样两个交叉位置，则交叉线上其余格可删该数字。 */
    private static boolean xWingDirection(int[] cand, int bit, boolean rowBased) {
        int[] pairMask = new int[9];
        for (int line = 0; line < 9; line++) {
            int[] cells = spots(cand, rowBased ? line : 9 + line, bit);
            if (cells.length != 2) {
                pairMask[line] = -1;
                continue;
            }
            int a = rowBased ? COL[cells[0]] : ROW[cells[0]];
            int b = rowBased ? COL[cells[1]] : ROW[cells[1]];
            pairMask[line] = (1 << a) | (1 << b);
        }
        for (int l1 = 0; l1 < 9; l1++) {
            if (pairMask[l1] < 0) {
                continue;
            }
            for (int l2 = l1 + 1; l2 < 9; l2++) {
                if (pairMask[l2] != pairMask[l1]) {
                    continue;
                }
                boolean changed = false;
                for (int cross = 0; cross < 9; cross++) {
                    if ((pairMask[l1] & (1 << cross)) == 0) {
                        continue;
                    }
                    for (int line = 0; line < 9; line++) {
                        if (line == l1 || line == l2) {
                            continue;
                        }
                        int cell = rowBased ? line * 9 + cross : cross * 9 + line;
                        if (cand[cell] != 0 && (cand[cell] & bit) != 0) {
                            cand[cell] &= ~bit;
                            changed = true;
                        }
                    }
                }
                if (changed) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 枢轴 {A,B}，两个翼 {A,C} 与 {B,C}，则同时看见两翼的格可删 C。 */
    private static boolean xyWing(int[] cand) {
        for (int pivot = 0; pivot < 81; pivot++) {
            int pivotMask = cand[pivot];
            if (pivotMask == 0 || Integer.bitCount(pivotMask) != 2) {
                continue;
            }
            int[] wings = new int[20];
            int wingCount = 0;
            for (int peer : peers(pivot)) {
                int peerMask = cand[peer];
                if (peerMask != 0 && Integer.bitCount(peerMask) == 2 && peerMask != pivotMask
                        && Integer.bitCount(peerMask & pivotMask) == 1) {
                    wings[wingCount++] = peer;
                }
            }
            for (int i = 0; i < wingCount; i++) {
                for (int j = i + 1; j < wingCount; j++) {
                    int mask1 = cand[wings[i]];
                    int mask2 = cand[wings[j]];
                    int shared = mask1 & mask2;
                    if (Integer.bitCount(shared) != 1 || (shared & pivotMask) != 0) {
                        continue;
                    }
                    boolean changed = false;
                    for (int peer : peers(wings[i])) {
                        if (peer == pivot || peer == wings[j] || cand[peer] == 0) {
                            continue;
                        }
                        if ((cand[peer] & shared) == 0) {
                            continue;
                        }
                        boolean seesOther = false;
                        for (int other : peers(wings[j])) {
                            if (other == peer) {
                                seesOther = true;
                                break;
                            }
                        }
                        if (!seesOther) {
                            continue;
                        }
                        cand[peer] &= ~shared;
                        changed = true;
                    }
                    if (changed) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static int[] spots(int[] cand, int unit, int bit) {
        int[] cells = new int[9];
        int count = 0;
        for (int slot = 0; slot < 9; slot++) {
            int cell = UNITS[unit][slot];
            if (cand[cell] != 0 && (cand[cell] & bit) != 0) {
                cells[count++] = cell;
            }
        }
        int[] result = new int[count];
        System.arraycopy(cells, 0, result, 0, count);
        return result;
    }

    private static int[] union(int[] a, int[] b, int[] c) {
        boolean[] seen = new boolean[81];
        int[] out = new int[27];
        int cursor = 0;
        for (int[] source : new int[][]{a, b, c}) {
            for (int cell : source) {
                if (!seen[cell]) {
                    seen[cell] = true;
                    out[cursor++] = cell;
                }
            }
        }
        int[] result = new int[cursor];
        System.arraycopy(out, 0, result, 0, cursor);
        return result;
    }

    private static boolean sameRow(int[] cells) {
        for (int cell : cells) {
            if (ROW[cell] != ROW[cells[0]]) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameCol(int[] cells) {
        for (int cell : cells) {
            if (COL[cell] != COL[cells[0]]) {
                return false;
            }
        }
        return true;
    }

    /** 在 unit 内、排除 keep 指定格，删掉这些格候选里的 mask。会造成空格无候选时整体回退。 */
    private static boolean eliminateOthers(int[] cand, int unit, int[] keep, int mask) {
        int[] before = new int[81];
        boolean changed = false;
        for (int slot = 0; slot < 9; slot++) {
            int cell = UNITS[unit][slot];
            if (cand[cell] == 0 || isKept(cell, keep) || (cand[cell] & mask) == 0) {
                continue;
            }
            before[cell] = cand[cell];
            cand[cell] &= ~mask;
            changed = true;
            if (cand[cell] == 0) {
                restore(cand, before);
                return false;
            }
        }
        return changed;
    }

    /** 在 targetUnit 内、排除属于 sourceUnit 的格，删掉这些格候选里的 bit。会造成空格无候选时整体回退。 */
    private static boolean eliminateOutside(int[] cand, int targetUnit, int sourceUnit, int bit) {
        int[] before = new int[81];
        boolean changed = false;
        for (int slot = 0; slot < 9; slot++) {
            int cell = UNITS[targetUnit][slot];
            if (inUnit(cell, sourceUnit) || cand[cell] == 0 || (cand[cell] & bit) == 0) {
                continue;
            }
            before[cell] = cand[cell];
            cand[cell] &= ~bit;
            changed = true;
            if (cand[cell] == 0) {
                restore(cand, before);
                return false;
            }
        }
        return changed;
    }

    private static boolean isKept(int cell, int[] keep) {
        for (int keeper : keep) {
            if (keeper == cell) {
                return true;
            }
        }
        return false;
    }

    private static void restore(int[] cand, int[] before) {
        for (int cell = 0; cell < 81; cell++) {
            if (before[cell] != 0) {
                cand[cell] = before[cell];
            }
        }
    }
}
