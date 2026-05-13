package weep12.cva.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import weep12.cva.domain.user.entity.User;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Spring Security UserDetails 实现，作为认证主体贯穿整个安全上下文。
 *
 * <p>相比默认的 {@code org.springframework.security.core.userdetails.User}，
 * 增加了业务主键 {@code id} 和邮箱字段，Role 代码通过 {@code ROLE_} 前缀
 * 转换为 Spring Security 权限标识。</p>
 *
 * <p>账户状态由 User 实体的 status 和 lockedUntil 字段驱动：
 * <ul>
 *   <li>status=0 → isEnabled()=false → DisabledException</li>
 *   <li>lockedUntil > now → isAccountNonLocked()=false → LockedException</li>
 * </ul></p>
 */
@Getter
public class UserDetailsImpl implements UserDetails {

    /** 业务主键，用于 Service 层查询和数据关联 */
    private final Long id;
    private final String username;
    private final String password;
    private final String email;
    private final boolean enabled;
    private final boolean locked;
    private final Collection<? extends GrantedAuthority> authorities;

    public UserDetailsImpl(Long id, String username, String password, String email,
                           boolean enabled, boolean locked,
                           Collection<? extends GrantedAuthority> authorities) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.email = email;
        this.enabled = enabled;
        this.locked = locked;
        this.authorities = authorities;
    }

    /**
     * 从 User 实体和角色列表构建 UserDetails。
     *
     * @param user      数据库用户实体
     * @param roleCodes 角色代码列表，如 [SUPER_ADMIN, MERCHANT]
     * @return Spring Security 认证主体
     */
    public static UserDetailsImpl build(User user, List<String> roleCodes) {
        List<GrantedAuthority> authorities = roleCodes != null && !roleCodes.isEmpty()
                ? roleCodes.stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .collect(Collectors.toList())
                : Collections.emptyList();
        return new UserDetailsImpl(
                user.getId(), user.getUsername(), user.getPassword(),
                user.getEmail(),
                // status=null 视为禁用（防御性处理）
                user.getStatus() != null && user.getStatus() == 1,
                user.isLocked(),
                authorities
        );
    }

    // -------- 账户状态检查（Spring Security 调用）--------

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return !locked; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return enabled; }
}
