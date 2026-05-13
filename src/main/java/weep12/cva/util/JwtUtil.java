package weep12.cva.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * JWT 令牌工具类——生成、校验、黑名单及刷新。
 *
 * <p>使用 HMAC-SHA 对称密钥签名，Token 有效期由 {@code jwt.expiration} 配置。
 * 登出后的 Token 存入 Redis 黑名单，过期时间与 Token 剩余有效期一致，自动清理。</p>
 *
 * <p>Redis 键格式: {@code jwt:blacklist:<token>}，值为 "1"，TTL=Token 剩余毫秒数。</p>
 */
@Slf4j
@Component
public class JwtUtil {

    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";

    private final String secret;
    private final long expiration;
    private final RedisTemplate<String, String> redisTemplate;

    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.expiration}") long expiration,
                   RedisTemplate<String, String> redisTemplate) {
        this.secret = secret;
        this.expiration = expiration;
        this.redisTemplate = redisTemplate;
    }

    /** 从配置的 secret 字符串构建 HMAC 签名密钥 */
    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 JWT Token。
     *
     * @param username 用户名，存入 subject 字段
     * @return 签名的 JWT 字符串
     */
    public String generateToken(String username) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expiration);
        return Jwts.builder()
                .subject(username)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key())
                .compact();
    }

    /**
     * 从 Token 中提取用户名。
     */
    public String getUsernameFromToken(String token) {
        return getClaims(token).getSubject();
    }

    /**
     * 校验 Token 是否有效。
     *
     * <p>检查项：签名合法性 → 是否在黑名单中 → 是否已过期。</p>
     *
     * @return true 表示 Token 有效且未被吊销
     */
    public boolean validateToken(String token) {
        try {
            if (isBlacklisted(token)) {
                log.debug("Token 已被吊销");
                return false;
            }
            return !getClaims(token).getExpiration().before(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT Token 校验失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 将 Token 加入 Redis 黑名单（用于登出和刷新场景）。
     * 黑名单记录的 TTL 等于 Token 剩余有效期，到期后自动清理。
     */
    public void blacklistToken(String token) {
        try {
            Claims claims = getClaims(token);
            long remaining = claims.getExpiration().getTime() - System.currentTimeMillis();
            if (remaining > 0) {
                redisTemplate.opsForValue().set(
                        BLACKLIST_PREFIX + token, "1", remaining, TimeUnit.MILLISECONDS);
                log.debug("Token 已加入黑名单，剩余 {}ms", remaining);
            }
        } catch (JwtException e) {
            log.warn("Token 加入黑名单失败: {}", e.getMessage());
        }
    }

    /**
     * 检查 Token 是否在黑名单中。
     */
    public boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + token));
    }

    /**
     * 刷新 Token——将旧 Token 加入黑名单并签发新 Token。
     *
     * @param oldToken 当前有效的旧 Token
     * @return 新签发的 JWT
     */
    public String refreshToken(String oldToken) {
        String username = getUsernameFromToken(oldToken);
        blacklistToken(oldToken);
        log.info("Token 已刷新: username={}", username);
        return generateToken(username);
    }

    /** 解析并返回 Token 的 Claims */
    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
