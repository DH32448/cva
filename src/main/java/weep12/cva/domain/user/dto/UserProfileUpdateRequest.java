package weep12.cva.domain.user.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 用户个人信息更新请求 DTO。
 *
 * <p>邮箱和手机号均为可选项——仅更新用户显式提交的字段。
 * 空值在 Service 层被忽略，不会覆盖原有数据。</p>
 */
@Data
public class UserProfileUpdateRequest {

    @Pattern(regexp = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$", message = "邮箱格式不正确")
    private String email;

    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;
}
