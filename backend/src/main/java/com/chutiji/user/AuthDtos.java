package com.chutiji.user;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 用户系统出入参。 */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank(message = "用户名不能为空")
            @Size(min = 3, max = 64, message = "用户名长度需在 3-64 之间")
            String username,

            @NotBlank(message = "密码不能为空")
            @Size(min = 6, max = 64, message = "密码长度需在 6-64 之间")
            String password,

            @Size(max = 64, message = "昵称最长 64 字符")
            String nickname) {
    }

    public record LoginRequest(
            @NotBlank(message = "用户名不能为空") String username,
            @NotBlank(message = "密码不能为空") String password) {
    }

    public record UpdateProfileRequest(
            @Size(max = 64, message = "昵称最长 64 字符") String nickname,
            @Size(min = 6, max = 64, message = "密码长度需在 6-64 之间") String password) {
    }

    public record UserView(Long userId, String username, String nickname, LocalDateTime createdAt) {
    }

    public record LoginResponse(String token, LocalDateTime expiresAt, UserView user) {
    }
}
