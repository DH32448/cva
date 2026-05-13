package weep12.cva.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import weep12.cva.result.Result;

/**
 * 全局异常处理器——将各类异常统一转换为 {@link Result} JSON 响应。
 *
 * <p>异常映射策略（按优先级从具体到通用）：
 * <ul>
 *   <li>参数校验失败 → 400</li>
 *   <li>认证相关（密码错误/锁定/禁用/未认证）→ 401/423/403/401</li>
 *   <li>权限不足 → 403</li>
 *   <li>数据完整性冲突 → 409</li>
 *   <li>业务异常 → 业务方指定 code</li>
 *   <li>未知异常 → 500（仅打印日志，不暴露内部细节）</li>
 * </ul></p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ==================== 400: 请求参数校验 ====================

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        FieldError fe = e.getBindingResult().getFieldError();
        String msg = fe != null ? fe.getDefaultMessage() : "参数校验失败";
        return Result.fail(400, msg);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return Result.fail(400, "缺少必要参数: " + e.getParameterName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        return Result.fail(400, "请求体格式错误");
    }

    // ==================== 401/403/423: 认证与授权 ====================

    @ExceptionHandler(BadCredentialsException.class)
    public Result<Void> handleBadCredentials(BadCredentialsException e) {
        return Result.fail(401, "用户名或密码错误");
    }

    /** 账户因连续登录失败被临时锁定 */
    @ExceptionHandler(LockedException.class)
    public Result<Void> handleLocked(LockedException e) {
        return Result.fail(423, "账户已被锁定，请稍后再试");
    }

    /** 账户被管理员禁用 */
    @ExceptionHandler(DisabledException.class)
    public Result<Void> handleDisabled(DisabledException e) {
        return Result.fail(403, "账户已被禁用");
    }

    /** 认证失败的兜底处理（优先级低于具体子类） */
    @ExceptionHandler(AuthenticationException.class)
    public Result<Void> handleAuthentication(AuthenticationException e) {
        return Result.fail(401, "认证失败");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public Result<Void> handleAccessDenied(AccessDeniedException e) {
        return Result.fail(403, "权限不足");
    }

    // ==================== 409: 数据冲突 ====================

    /** 数据库唯一约束冲突——记录日志但不暴露表结构信息 */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public Result<Void> handleDataIntegrity(DataIntegrityViolationException e) {
        log.warn("数据完整性冲突", e);
        return Result.fail(409, "数据冲突，请检查输入");
    }

    // ==================== 业务异常 ====================

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        log.warn("业务异常: [{}] {}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    // ==================== 500: 未知异常兜底 ====================

    /** 所有未显式捕获的异常统一返回 500，异常详情仅写入日志 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("服务器内部异常", e);
        return Result.fail(500, "服务器内部错误");
    }
}
