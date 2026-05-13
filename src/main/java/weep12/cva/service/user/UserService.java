package weep12.cva.service.user;

import weep12.cva.domain.user.entity.User;

import java.util.List;

/**
 * 用户服务接口——定义用户生命周期管理及安全相关操作。
 */
public interface UserService {

    /** 根据 ID 查询用户，返回 null 表示不存在 */
    User findById(Long id);

    /**
     * 注册新用户。
     * 前置校验：用户名/邮箱/手机号唯一性；数据库唯一约束兜底。
     *
     * @return 新用户 ID
     */
    Long register(User user);

    /** 更新个人信息（邮箱、手机号），空值不覆盖 */
    void updateProfile(Long userId, String email, String phone);

    /** 修改密码，需验证旧密码 */
    void changePassword(Long userId, String oldPassword, String newPassword);

    /** 检查邮箱是否已被注册 */
    boolean isEmailTaken(String email);

    /** 检查手机号是否已被注册 */
    boolean isPhoneTaken(String phone);

    /** 查询全部用户（管理员） */
    List<User> findAll();

    /** 启用/禁用用户（管理员） */
    void updateStatus(Long id, Integer status);

    /** 删除用户（管理员） */
    void deleteUser(Long id);

    /** 记录登录失败，连续 5 次失败自动锁定 30 分钟 */
    void recordLoginFailure(String username);

    /** 登录成功后重置失败计数 */
    void recordLoginSuccess(String username);

    /**
     * 根据邮箱查询用户——用于验证码登录。
     * 返回 null 表示邮箱未注册。
     */
    User findByEmail(String email);

    /**
     * 查询用户角色代码列表。
     */
    List<String> findRoleCodesByUserId(Long userId);

    /**
     * 验证邮箱——将用户状态置为启用。
     *
     * @param email 要验证的邮箱
     * @throws BusinessException 若用户不存在
     */
    void verifyEmail(String email);
}
