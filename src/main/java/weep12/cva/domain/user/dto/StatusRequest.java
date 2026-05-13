package weep12.cva.domain.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 用户状态更新请求 DTO（管理员操作）。
 *
 * <p>status: 0-禁用, 1-启用。</p>
 */
@Data
public class StatusRequest {

    @NotNull(message = "状态不能为空")
    @Min(value = 0, message = "状态值只能为 0 或 1")
    @Max(value = 1, message = "状态值只能为 0 或 1")
    private Integer status;
}
