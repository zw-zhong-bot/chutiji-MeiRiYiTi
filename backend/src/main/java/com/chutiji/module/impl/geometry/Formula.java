package com.chutiji.module.impl.geometry;

/**
 * 生成路径标识：{@link ShapeKind} × 复杂度 = 十条路径，每条路径固定用一条公式。
 *
 * <p>它跟着答案一起进 answer，让「图形-答案一致性」的验收测试能无歧义地知道该用哪条公式验算，
 * 也让五类题型的覆盖统计不会因为随机抽题而漏掉某条路径。</p>
 */
enum Formula {
    TRIANGLE_PERIMETER,
    TRIANGLE_AREA,
    RECTANGLE_PERIMETER,
    RECTANGLE_DIAGONAL,
    CIRCLE_CIRCUMFERENCE,
    CIRCLE_AREA,
    PYTHAGORAS_HYPOTENUSE,
    PYTHAGORAS_LEG,
    SIMILAR_SIDE,
    SIMILAR_PERIMETER
}
