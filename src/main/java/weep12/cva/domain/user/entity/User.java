package weep12.cva.domain.user.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 用户实体，映射 user 表。
 *
 * <p>密码字段仅用于 MyBatis 数据读写；API 响应通过 VO 层过滤，
 * 不会将密码暴露给客户端。</p>
 */
@Getter
@Setter
public class User {

    private Long id;
    private String username;
    private String password;
    private String phone;
    /** 账户状态：0-禁用, 1-启用 */
    private Integer status;
    /** 连续登录失败次数，成功登录后归零 */
    private Integer loginFailures;
    /** 锁定截止时间，null 或过去时间表示未锁定 */
    private LocalDateTime lockedUntil;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 判断当前账户是否处于锁定状态。
     *
     * @return true 如果 lockedUntil 不为 null 且在当前时间之后
     */
    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now());
    }
}
