package com.chutiji.review;

/**
 * 一次调度的结果：新状态 + 下次间隔（天，已按 [1, 365] 护栏收敛）。
 */
public record FsrsResult(FsrsState next, double intervalDays) {
}
