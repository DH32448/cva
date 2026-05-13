package weep12.cva.service.user.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import weep12.cva.domain.user.entity.User;
import weep12.cva.exception.BusinessException;
import weep12.cva.mapper.user.UserMapper;
import weep12.cva.service.user.UserService;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户服务实现——核心业务逻辑层。
 *
 * <p>所有写操作标注 {@code @Transactional(rollbackFor = Exception.class)}，
 * 确保数据库操作原子性。业务规则冲突统一抛出 BusinessException 由全局异常处理器转换。</p>
 *
 * <p>登录安全机制：连续 5 次失败后锁定 30 分钟，成功登录自动解锁并清零失败计数。</p>
 */
@Slf4j
@Service
public class UserServiceImpl implements UserService {

    /** 触发账户锁定的失败次数阈值 */
    private static final int MAX_LOGIN_FAILURES = 5;
    /** 锁定持续时间（分钟） */
    private static final long LOCK_DURATION_MINUTES = 30;

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public User findById(Long id) {
        return userMapper.findById(id);
    }

    /**
     * 注册流程：
     * <ol>
     *   <li>校验用户名唯一性</li>
     *   <li>校验邮箱唯一性（仅当传入非空值时）</li>
     *   <li>校验手机号唯一性（仅当传入非空值时）</li>
     *   <li>执行 INSERT——捕获数据库唯一约束冲突作为并发兜底</li>
     * </ol>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long register(User user) {
        if (isUsernameTaken(user.getUsername())) {
            throw new BusinessException(409, "用户名已被占用");
        }
        if (StringUtils.hasText(user.getEmail()) && isEmailTaken(user.getEmail())) {
            throw new BusinessException(409, "邮箱已被注册");
        }
        if (StringUtils.hasText(user.getPhone()) && isPhoneTaken(user.getPhone())) {
            throw new BusinessException(409, "手机号已被注册");
        }
        try {
            userMapper.insert(user);
        } catch (DataIntegrityViolationException e) {
            // 并发注册或应用层检查与数据库不一致时的兜底处理
            throw new BusinessException(409, "用户名或邮箱已存在");
        }
        log.info("用户注册: id={}, username={}", user.getId(), user.getUsername());
        return user.getId();
    }

    /**
     * 更新个人信息——仅更新非空字段。
     * 对传入的邮箱/手机号做"排除自身"的唯一性校验。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProfile(Long userId, String email, String phone) {
        if (StringUtils.hasText(email) && isEmailTakenByOther(userId, email)) {
            throw new BusinessException(409, "邮箱已被其他用户使用");
        }
        if (StringUtils.hasText(phone) && isPhoneTakenByOther(userId, phone)) {
            throw new BusinessException(409, "手机号已被其他用户使用");
        }
        int result = userMapper.updateProfile(userId, email, phone);
        if (result == 0) {
            throw new BusinessException(404, "用户不存在");
        }
        log.info("用户信息更新: id={}", userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        // BCrypt 慢比较，防时序攻击
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException(400, "原密码不正确");
        }
        userMapper.updatePassword(userId, passwordEncoder.encode(newPassword));
        log.info("用户密码修改成功: id={}", userId);
    }

    @Override
    public boolean isEmailTaken(String email) {
        return userMapper.findByEmail(email) != null;
    }

    @Override
    public boolean isPhoneTaken(String phone) {
        return userMapper.findByPhone(phone) != null;
    }

    /** 检查邮箱是否被其他用户占用（用于修改个人信息场景） */
    private boolean isEmailTakenByOther(Long userId, String email) {
        User existing = userMapper.findByEmail(email);
        return existing != null && !existing.getId().equals(userId);
    }

    /** 检查手机号是否被其他用户占用（用于修改个人信息场景） */
    private boolean isPhoneTakenByOther(Long userId, String phone) {
        User existing = userMapper.findByPhone(phone);
        return existing != null && !existing.getId().equals(userId);
    }

    private boolean isUsernameTaken(String username) {
        return userMapper.findByUsername(username) != null;
    }

    @Override
    public List<User> findAll() {
        return userMapper.findAll();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        if (userMapper.findById(id) == null) {
            throw new BusinessException(404, "用户不存在");
        }
        userMapper.updateStatus(id, status);
        log.info("用户状态更新: id={}, status={}", id, status);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long id) {
        if (userMapper.findById(id) == null) {
            throw new BusinessException(404, "用户不存在");
        }
        userMapper.deleteById(id);
        log.info("用户已删除: id={}", id);
    }

    /**
     * 记录登录失败。
     *
     * <p>每次调用 incrementLoginFailures 自增失败计数。
     * 判断条件使用 {@code >= 4} 而非 {@code >= 5}，因为数据库中的值是更新前的值
     * ——incrementLoginFailures 在判断之后执行，所以当前值 >= 4 意味着下一次将是第 5 次失败。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordLoginFailure(String username) {
        User user = userMapper.findByUsername(username);
        if (user == null) return;
        userMapper.incrementLoginFailures(user.getId());
        if (user.getLoginFailures() != null && user.getLoginFailures() >= MAX_LOGIN_FAILURES - 1) {
            userMapper.lockAccount(user.getId(),
                    LocalDateTime.now().plusMinutes(LOCK_DURATION_MINUTES));
            log.warn("用户账户已被锁定: username={}", username);
        }
    }

    /**
     * 登录成功后重置失败计数并解除锁定。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordLoginSuccess(String username) {
        User user = userMapper.findByUsername(username);
        if (user != null) {
            userMapper.resetLoginFailures(user.getId());
        }
    }

    @Override
    public User findByEmail(String email) {
        return userMapper.findByEmail(email);
    }

    @Override
    public List<String> findRoleCodesByUserId(Long userId) {
        return userMapper.findRoleCodesByUserId(userId);
    }

    /**
     * 验证邮箱——标记 email_verified=1, status=1。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void verifyEmail(String email) {
        User user = userMapper.findByEmail(email);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        userMapper.verifyEmail(user.getId());
        log.info("邮箱验证成功: email={}", email);
    }
}
