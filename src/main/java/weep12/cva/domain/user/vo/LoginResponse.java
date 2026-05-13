package weep12.cva.domain.user.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 登录/Token 刷新响应 VO。
 *
 * <p>包含 JWT Token、用户基本信息及角色列表。
 * roles 用于前端进行菜单/按钮级别的权限控制，
 * refresh 接口返回时 roles 为 null。</p>
 */
@Data
@AllArgsConstructor
public class LoginResponse {

    private String token;
    private Long userId;
    private String username;
    private List<String> roles;
}
