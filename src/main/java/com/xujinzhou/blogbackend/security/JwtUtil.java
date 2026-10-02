package com.xujinzhou.blogbackend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtUtil {

    // 密钥：用来加密/验证Token，真实项目中应该放到配置文件里，不要写死在代码里
    private final SecretKey key = Keys.hmacShaKeyFor(
            "xujinzhou-blog-secret-key-must-be-long-enough-2026".getBytes()
    );

    private final long expireMillis = 24 * 60 * 60 * 1000;   // Token有效期：24小时

    // 生成Token
    public String generateToken(String username) {
        return Jwts.builder()
                .subject(username)                                    // Token里存的是哪个用户
                .issuedAt(new Date())                                  // 签发时间
                .expiration(new Date(System.currentTimeMillis() + expireMillis))  // 过期时间
                .signWith(key)                                         // 用密钥签名
                .compact();
    }

    // 从Token中解析出用户名
    public String getUsernameFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getSubject();
    }

    // 验证Token是否合法（没过期、签名没被篡改）
    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;   // 解析失败：过期了、被篡改了、格式不对，都会在这里被捕获
        }
    }
}
