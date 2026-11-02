package com.chutiji.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chutiji.common.ApiResult;
import com.chutiji.security.CurrentUser;
import com.chutiji.user.AuthDtos.LoginRequest;
import com.chutiji.user.AuthDtos.LoginResponse;
import com.chutiji.user.AuthDtos.RegisterRequest;
import com.chutiji.user.AuthDtos.UpdateProfileRequest;
import com.chutiji.user.AuthDtos.UserView;
import com.chutiji.user.AuthService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiResult<UserView> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResult.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResult<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResult.ok(authService.login(request));
    }

    @GetMapping("/me")
    public ApiResult<UserView> me() {
        return ApiResult.ok(authService.currentUser(CurrentUser.requireId()));
    }

    @PutMapping("/me")
    public ApiResult<UserView> updateMe(@Valid @RequestBody UpdateProfileRequest request) {
        return ApiResult.ok(authService.updateProfile(CurrentUser.requireId(), request));
    }
}
