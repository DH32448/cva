package weep12.cva.exception;

/**
 * 业务异常——Service 层检测到业务规则冲突时抛出。
 *
 * <p>由 {@link GlobalExceptionHandler#handleBusiness} 统一捕获并转换为 Result JSON 响应。
 * code 字段对应 HTTP 语义状态码，前端据此做差异化处理。</p>
 *
 * <p>常见用法：
 * <pre>{@code
 * throw new BusinessException(409, "用户名已存在");
 * throw new BusinessException(404, "用户不存在");
 * throw new BusinessException(400, "原密码不正确");
 * }</pre></p>
 */
public class BusinessException extends RuntimeException {

    /** HTTP 语义状态码 */
    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
