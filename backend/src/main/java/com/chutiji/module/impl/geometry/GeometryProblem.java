package com.chutiji.module.impl.geometry;

import java.util.Map;

/**
 * 一道几何题的完整产物。
 *
 * <p>{@code given} 是这道题「画在图上、写在题面里」的全部已知量——同一份参数既进
 * {@link SvgCanvas} 生成图形，也进答案计算，图形与答案不可能对不上。</p>
 */
record GeometryProblem(
        Formula formula,
        String prompt,
        double value,
        Map<String, Double> given,
        String hint,
        String svg,
        String explanation) {
}
