package com.chutiji.security;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/** HS256 JWT 签发与校验，有效期默认 7 天。 */
@Service
public class JwtService {

    private final SecretKey key;
    private final int expireDays;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expire-days:7}") int expireDays) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("app.jwt.secret 至少需要 32 字节，否则 HS256 不安全");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expireDays = expireDays;
    }

    public record IssuedToken(String token, LocalDateTime expiresAt) {
    }

    public IssuedToken issue(Long userId, String username) {
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(expireDays);
        Date expiry = Date.from(expiresAt.atZone(ZoneId.systemDefault()).toInstant());
        String token = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .issuedAt(new Date())
                .expiration(expiry)
                .signWith(key, Jwts.SIG.HS256)
                .compact();
        return new IssuedToken(token, expiresAt);
    }

    /** 校验并取出 userId；无效或过期返回 null。 */
    public Long verify(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return Long.valueOf(claims.getSubject());
        } catch (JwtException | IllegalArgumentException ex) {
            return null;
        }
    }
}
