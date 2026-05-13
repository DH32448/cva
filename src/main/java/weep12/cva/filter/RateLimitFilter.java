package weep12.cva.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import weep12.cva.result.Result;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * 固定窗口限流过滤器——仅对 /api/auth/** 路径生效（登录、注册等）。
 *
 * <p>每个 IP 在 60 秒内最多发起 10 次认证请求，超出返回 429。
 * 使用 Redis INCR + EXPIRE 原子操作实现，支持多实例部署。</p>
 *
 * <p>Redis 键格式: {@code ratelimit:<client_ip>}，首次请求设置 60s TTL。</p>
 */
@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    /** 窗口内最大请求数 */
    private static final int MAX_REQUESTS = 10;
    /** 窗口时长（秒） */
    private static final int WINDOW_SECONDS = 60;
    /** Redis 键前缀 */
    private static final String PREFIX = "ratelimit:";

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(RedisTemplate<String, String> redisTemplate,
                           ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 仅对认证路径限流。
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String clientIp = getClientIp(request);
        String key = PREFIX + clientIp;

        // Redis INCR 是原子操作，首次调用时值为 1，此时设置过期时间
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, WINDOW_SECONDS, TimeUnit.SECONDS);
        }

        // 超出限制直接返回 429，不继续执行后续过滤器
        if (count != null && count > MAX_REQUESTS) {
            log.warn("请求被限流: ip={}, count={}", clientIp, count);
            response.setContentType("application/json;charset=utf-8");
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.getWriter().write(objectMapper.writeValueAsString(
                    Result.fail(429, "请求过于频繁，请稍后再试")));
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 获取客户端真实 IP——优先从 X-Forwarded-For 头取（适用于反向代理后）。
     */
    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
