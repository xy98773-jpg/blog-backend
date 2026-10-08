package com.xujinzhou.blogbackend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

    /** 开发默认密钥的前缀，用于启动时提示"你正在用默认密钥" */
    private static final String DEV_DEFAULT_PREFIX = "xujinzhou-blog-dev-only";

    /** HS384 要求密钥至少 48 字节，这里按 32 字节做最低门槛校验 */
    private static final int MIN_KEY_BYTES = 32;

    private final SecretKey key;
    private final long expireMillis;

    /**
     * 通过构造方法注入配置（推荐做法，比字段注入好）
     *
     * 为什么密钥要从配置文件读，而不是写死在代码里？
     *   ① 安全：密钥写死在代码里，一旦代码泄露（开源、离职带代码、仓库被看到），
     *      任何人都能伪造出合法 Token，鉴权形同虚设。
     *   ② 环境隔离：开发/测试/生产的密钥必须不同，写死就没法区分。
     *   ③ 可轮换：怀疑密钥泄露时，改一个环境变量就能让所有旧 Token 立刻失效。
     *
     * @param secret       签名密钥（来自配置 jwt.secret，默认值仅供本地开发）
     * @param expireMillis Token 有效期（毫秒）
     */
    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.expire-millis:86400000}") long expireMillis) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);

        // 快速失败（Fail Fast）：密钥太短时直接拒绝启动，
        // 避免"签名算法很弱但没人发现"这种隐患被带到生产环境
        if (keyBytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException(
                    "jwt.secret 太短（当前 " + keyBytes.length + " 字节），至少需要 " + MIN_KEY_BYTES + " 字节。"
                            + "请通过环境变量 JWT_SECRET 配置一个足够长的随机字符串。");
        }

        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.expireMillis = expireMillis;

        if (secret.startsWith(DEV_DEFAULT_PREFIX)) {
            log.warn("⚠️ 正在使用【开发默认】JWT 密钥！生产环境请务必通过环境变量 JWT_SECRET 覆盖。");
        }
        log.info("JwtUtil 初始化完成：密钥长度={}字节，Token有效期={}小时",
                keyBytes.length, expireMillis / 1000 / 60 / 60);
    }

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
