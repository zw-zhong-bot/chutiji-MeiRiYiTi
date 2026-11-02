package com.chutiji.security;

/** 当前请求的登录用户，由 AuthInterceptor 写入、请求结束时清理。 */
public final class CurrentUser {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    private CurrentUser() {
    }

    public static void set(Long userId) {
        USER_ID.set(userId);
    }

    public static Long id() {
        return USER_ID.get();
    }

    public static Long requireId() {
        Long id = USER_ID.get();
        if (id == null) {
            throw new IllegalStateException("未登录上下文");
        }
        return id;
    }

    public static void clear() {
        USER_ID.remove();
    }
}
