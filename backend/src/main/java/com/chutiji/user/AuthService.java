package com.chutiji.user;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chutiji.common.BizException;
import com.chutiji.security.JwtService;
import com.chutiji.user.AuthDtos.LoginRequest;
import com.chutiji.user.AuthDtos.LoginResponse;
import com.chutiji.user.AuthDtos.RegisterRequest;
import com.chutiji.user.AuthDtos.UpdateProfileRequest;
import com.chutiji.user.AuthDtos.UserView;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public UserView register(RegisterRequest request) {
        String username = request.username().trim();
        if (userRepository.existsByUsername(username)) {
            throw new BizException(2004, "用户名已存在");
        }
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname() == null || request.nickname().isBlank()
                ? username
                : request.nickname().trim());
        user.setRole("USER");
        user.setEnabled(Boolean.TRUE);
        UserEntity saved = userRepository.save(user);
        return toView(userRepository.findById(saved.getId()).orElse(saved));
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        UserEntity user = userRepository.findByUsername(request.username().trim())
                .orElseThrow(() -> new BizException(2005, "用户名或密码错误"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BizException(2005, "用户名或密码错误");
        }
        if (Boolean.FALSE.equals(user.getEnabled())) {
            throw new BizException(2006, "账号已被禁用");
        }
        JwtService.IssuedToken issued = jwtService.issue(user.getId(), user.getUsername());
        return new LoginResponse(issued.token(), issued.expiresAt(), toView(user));
    }

    @Transactional(readOnly = true)
    public UserView currentUser(Long userId) {
        return toView(load(userId));
    }

    @Transactional
    public UserView updateProfile(Long userId, UpdateProfileRequest request) {
        UserEntity user = load(userId);
        if (request.nickname() != null && !request.nickname().isBlank()) {
            user.setNickname(request.nickname().trim());
        }
        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        return toView(userRepository.save(user));
    }

    private UserEntity load(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BizException(2007, "用户不存在"));
    }

    private UserView toView(UserEntity user) {
        return new UserView(user.getId(), user.getUsername(), user.getNickname(), user.getCreatedAt());
    }
}
