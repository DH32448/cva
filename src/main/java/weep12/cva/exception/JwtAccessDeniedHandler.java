package weep12.cva.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import weep12.cva.result.Result;

import java.io.IOException;

/**
 * 访问拒绝处理器——已认证但无权访问时返回 403 JSON。
 *
 * <p>触发场景：用户已登录但角色不满足 @PreAuthorize 要求。
 * 由于 Spring Security Filter 层无法被 @RestControllerAdvice 捕获，
 * 需要实现 AccessDeniedHandler 接口直接写入 HTTP 响应。</p>
 */
@Slf4j
@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public JwtAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        log.warn("访问被拒绝: uri={}", request.getRequestURI());
        response.setContentType("application/json;charset=utf-8");
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(403, "权限不足")));
    }
}
