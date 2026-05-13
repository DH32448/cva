package weep12.cva.mapper.user;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import weep12.cva.domain.user.entity.User;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户 Mapper 接口——对应 src/main/resources/mapper/user/UserMapper.xml 中的 SQL 映射。
 */
@Mapper
public interface UserMapper {

    // ==================== 查询 ====================

    /** 根据用户名精确查询——返回含密码的完整实体，用于认证 */
    User findByUsername(String username);

    /** 根据 ID 查询 */
    User findById(Long id);

    /** 根据手机号查询——用于唯一性校验 */
    User findByPhone(String phone);

    /** 查询用户角色代码列表——用于构建 GrantedAuthority */
    List<String> findRoleCodesByUserId(Long userId);

    /** 查询全部用户——不含密码字段 */
    List<User> findAll();

    // ==================== 写入 ====================

    /** 新增用户，返回自增主键 */
    int insert(User user);

    /** 更新个人信息（手机号） */
    int updateProfile(@Param("id") Long id,
                      @Param("phone") String phone);

    /** 更新密码（已 BCrypt 加密） */
    int updatePassword(@Param("id") Long id,
                       @Param("password") String password);

    // ==================== 登录安全 ====================

    /** 登录失败计数 +1 */
    int incrementLoginFailures(@Param("id") Long id);

    /** 成功后重置失败计数并解除锁定 */
    int resetLoginFailures(@Param("id") Long id);

    /** 锁定账户至指定时间 */
    int lockAccount(@Param("id") Long id,
                    @Param("lockedUntil") LocalDateTime lockedUntil);

    /** 手动解锁 */
    int unlockAccount(@Param("id") Long id);

    // ==================== 管理操作 ====================

    /** 设置用户状态：0-禁用, 1-启用 */
    int updateStatus(@Param("id") Long id,
                     @Param("status") Integer status);

    /** 删除用户 */
    int deleteById(Long id);
}
