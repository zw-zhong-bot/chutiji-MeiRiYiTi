package com.chutiji.module.impl.geometry;

/** 几何题型：每个取值对应一类参数化题目。 */
public enum ShapeKind {
    /** 三角形周长 / 面积。 */
    TRIANGLE,
    /** 矩形周长 / 面积。 */
    RECTANGLE,
    /** 圆的周长 / 面积。 */
    CIRCLE,
    /** 勾股定理：已知两边求第三边。 */
    PYTHAGORAS,
    /** 相似三角形：按相似比求对应边。 */
    SIMILAR_TRIANGLE
}
