package weep12.cva.domain.user.vo;

import lombok.Data;
import weep12.cva.domain.user.entity.User;

import java.time.LocalDateTime;

/**
 * 用户个人信息响应 VO。
 * 返回不包含密码敏感字段的用户信息。
 */
@Data
public class UserProfileResponse {

    private Long id;
    private String username;
    private String email;
    private String phone;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static UserProfileResponse from(User user) {
        UserProfileResponse vo = new UserProfileResponse();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setStatus(user.getStatus());
        vo.setCreatedAt(user.getCreatedAt());
        vo.setUpdatedAt(user.getUpdatedAt());
        return vo;
    }
}
