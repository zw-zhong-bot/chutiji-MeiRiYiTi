package com.chutiji.module.api;

/** 判题结果。correctAnswer 只在判完之后才下发。 */
public record GradeResult(boolean correct, String correctAnswer, String explanation) {
}
