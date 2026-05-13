package weep12.cva.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import weep12.cva.result.Result;

import java.io.IOException;

/**
 * 认证入口点——未认证用户访问受保护资源时返回 401 JSON。
 *
 * <p>触发场景：未携带 Token 或 Token 无效/过期。
 * 同样因为位于 Filter 层，需直接写入 HTTP 响应而非依赖 @RestControllerAdvice。</p>
 */
@Slf4j
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        log.debug("未认证请求: uri={}", request.getRequestURI());
        response.setContentType("application/json;charset=utf-8");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.getWriter().write(objectMapper.writeValueAsString(
                Result.fail(401, "未认证，请先登录")));
    }
}
