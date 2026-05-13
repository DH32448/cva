package weep12.cva.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import weep12.cva.domain.user.entity.User;
import weep12.cva.mapper.user.UserMapper;

import java.util.List;

/**
 * 自定义 UserDetailsService——从数据库加载用户认证信息。
 *
 * <p>供 Spring Security 的 {@code DaoAuthenticationProvider} 调用。
 * 加载内容：用户实体（含 BCrypt 密码和账户状态）+ 角色权限列表。</p>
 */
@Slf4j
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserMapper userMapper;

    public UserDetailsServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userMapper.findByUsername(username);
        if (user == null) {
            log.warn("登录尝试——用户不存在: {}", username);
            throw new UsernameNotFoundException("用户不存在: " + username);
        }
        // 加载用户角色——用于 @PreAuthorize 方法级权限控制
        List<String> roleCodes = userMapper.findRoleCodesByUserId(user.getId());
        log.debug("用户角色加载: username={}, roles={}", username, roleCodes);
        return UserDetailsImpl.build(user, roleCodes);
    }
}
