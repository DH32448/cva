package weep12.cva.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import weep12.cva.util.JwtUtil;

import java.io.IOException;

/**
 * JWT 认证过滤器——每个请求执行一次（继承 OncePerRequestFilter）。
 *
 * <p>从 Authorization 头提取 Bearer Token，验证通过后将用户信息写入 SecurityContext，
 * 供后续 Spring Security 和业务代码使用。</p>
 *
 * <p>跳过 /api/auth/** 路径（由 {@link #shouldNotFilter} 控制），
 * 因为这些端点的认证由 Controller 自行处理（如登录时调用 AuthenticationManager）。</p>
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, UserDetailsService userDetailsService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
    }

    /**
     * 跳过认证接口——这些端点公开访问，无需 JWT 校验。
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = extractToken(request);

        if (token != null && jwtUtil.validateToken(token)) {
            // 从 Token 中恢复用户身份
            String username = jwtUtil.getUsernameFromToken(token);
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            // 第三个参数 credentials 传 null，因 JWT 模式下无凭证
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("JWT 认证成功: username={}", username);
        }

        // 无论 Token 是否有效都继续执行——认证/授权决策由后续 Filter 和 @PreAuthorize 处理
        filterChain.doFilter(request, response);
    }

    /**
     * 从请求头提取 Bearer Token。
     *
     * @return Token 字符串，若无则返回 null
     */
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
