package weep12.cva.result;

import lombok.Data;

/**
 * 统一 API 响应体。
 *
 * <p>所有 Controller 返回值通过此类型包装，确保前端接收到的 JSON 结构一致。
 * 泛型 T 为业务数据的具体类型。</p>
 *
 * <p>响应示例:
 * <pre>{@code
 * {"code": 200, "message": "success", "data": {...}}
 * {"code": 401, "message": "用户名或密码错误", "data": null}
 * }</pre></p>
 */
@Data
public class Result<T> {

    /** HTTP 语义状态码 */
    private int code;
    /** 提示信息 */
    private String message;
    /** 业务数据载荷 */
    private T data;

    /**
     * 成功响应（带数据）。
     *
     * @param data 业务数据
     * @param <T>  数据类型
     * @return code=200 的 Result
     */
    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.code = 200;
        r.message = "success";
        r.data = data;
        return r;
    }

    /**
     * 成功响应（无数据）。
     */
    public static <T> Result<T> ok() {
        return ok(null);
    }

    /**
     * 失败响应。
     *
     * @param code    HTTP 语义状态码（400/401/403/404/409/429/500...）
     * @param message 错误描述
     * @param <T>     数据类型
     * @return Result 对象，data 为 null
     */
    public static <T> Result<T> fail(int code, String message) {
        Result<T> r = new Result<>();
        r.code = code;
        r.message = message;
        return r;
    }
}
