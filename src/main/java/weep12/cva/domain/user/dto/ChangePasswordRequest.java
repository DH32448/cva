package weep12.cva.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改密码请求 DTO。
 *
 * <p>需提供旧密码进行身份验证，新密码长度 6-100 字符。</p>
 */
@Data
public class ChangePasswordRequest {

    @NotBlank(message = "原密码不能为空")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 100, message = "密码长度应在 6-100 个字符之间")
    private String newPassword;
}
