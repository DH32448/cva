package weep12.cva.domain.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 验证码登录请求 DTO。
 *
 * <p>使用邮箱 + 6位验证码登录，无需密码。</p>
 */
@Data
public class LoginByCodeRequest {

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    @NotBlank(message = "验证码不能为空")
    private String code;
}
