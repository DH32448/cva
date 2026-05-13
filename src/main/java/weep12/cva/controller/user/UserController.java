package weep12.cva.controller.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import weep12.cva.domain.user.dto.*;
import weep12.cva.domain.user.entity.User;
import weep12.cva.domain.user.vo.LoginResponse;
import weep12.cva.domain.user.vo.UserProfileResponse;
import weep12.cva.exception.BusinessException;
import weep12.cva.result.Result;
import weep12.cva.security.UserDetailsImpl;
import weep12.cva.service.mail.EmailService;
import weep12.cva.service.mail.VerificationCodeService;
import weep12.cva.service.user.UserService;
import weep12.cva.util.JwtUtil;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户控制器——认证、个人信息管理及后台管理接口。
 *
 * <p>路径分为三组：
 * <ul>
 *   <li>{@code /api/auth/** }    —— 公开访问：登录、注册、登出、Token 刷新</li>
 *   <li>{@code /api/user/** }    —— 需认证：个人信息查看/修改、 密码修改</li>
 *   <li>{@code /api/admin/** }   —— 需 SUPER_ADMIN 角色：用户管理</li>
 * </ul></p>
 *
 * <p>登录流程说明：
 * 通过 {@code AuthenticationManager.authenticate()} 触发 Spring Security 认证链，
 * 其中 {@code DaoAuthenticationProvider} 调用自定义 UserDetailsService 加载用户数据并校验密码。
 * 登录成功/失败分别记录到数据库，用于账户锁定机制。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api")
public class UserController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final VerificationCodeService verificationCodeService;

    public UserController(AuthenticationManager authenticationManager,
                          JwtUtil jwtUtil,
                          UserService userService,
                          PasswordEncoder passwordEncoder,
                          EmailService emailService,
                          VerificationCodeService verificationCodeService) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.verificationCodeService = verificationCodeService;
    }

    // ========================================================================
    // 公开接口 —— /api/auth/**
    // ========================================================================

    /**
     * 用户登录——验证用户名/密码，返回 JWT Token。
     *
     * <p>捕获 BadCredentialsException 以记录登录失败（用于账户锁定），
     * 其他认证异常（DisabledException、LockedException）由全局异常处理器处理。</p>
     */
    @PostMapping("/auth/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(), request.getPassword()));
            UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

            // 登录成功——重置失败计数和锁定状态
            userService.recordLoginSuccess(userDetails.getUsername());

            // 签发 JWT 并提取角色信息
            String token = jwtUtil.generateToken(userDetails.getUsername());
            List<String> roles = userDetails.getAuthorities().stream()
                    .map(a -> a.getAuthority().replace("ROLE_", ""))
                    .collect(Collectors.toList());

            log.info("用户登录成功: {}", userDetails.getUsername());
            return Result.ok(new LoginResponse(
                    token, userDetails.getId(),
                    userDetails.getUsername(), userDetails.getEmail(), roles));
        } catch (BadCredentialsException e) {
            // 记录失败——连续 5 次锁定 30 分钟
            userService.recordLoginFailure(request.getUsername());
            throw e; // 由 GlobalExceptionHandler 转换为 401
        }
    }

    /**
     * 用户注册——创建新账户，默认状态为启用。
     */
    @PostMapping("/auth/register")
    public Result<String> register(@Valid @RequestBody RegisterRequest request) {
        User user = new User();
        user.setUsername(request.getUsername());
        // 密码在入库前 BCrypt 加密
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        userService.register(user);
        log.info("用户注册成功: {}", request.getUsername());
        return Result.ok("注册成功");
    }

    /**
     * 登出——将当前 Token 加入 Redis 黑名单并清除安全上下文。
     */
    @PostMapping("/auth/logout")
    public Result<String> logout(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            // 黑名单 TTL = Token 剩余有效期，到期自动清理
            jwtUtil.blacklistToken(token);
        }
        SecurityContextHolder.clearContext();
        return Result.ok("已登出");
    }

    /**
     * Token 刷新——旧 Token 加入黑名单，签发新 Token。
     *
     * <p>要求旧 Token 仍然有效（未过期且未在黑名单中）。
     * 刷新后的响应不含 roles 字段（前端应仅在登录时缓存角色信息）。</p>
     */
    @PostMapping("/auth/refresh")
    public Result<LoginResponse> refreshToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return Result.fail(401, "未提供有效的 Token");
        }
        String oldToken = header.substring(7);
        if (!jwtUtil.validateToken(oldToken)) {
            return Result.fail(401, "Token 无效或已过期");
        }
        String newToken = jwtUtil.refreshToken(oldToken);
        UserDetailsImpl principal = getCurrentUser();
        return Result.ok(new LoginResponse(
                newToken, principal.getId(),
                principal.getUsername(), principal.getEmail(), null));
    }

    /**
     * 发送邮箱验证码——用于登录或注册。
     *
     * <p>type=login: 向已注册邮箱发送登录验证码。
     * type=register: 向未注册邮箱发送注册验证码。</p>
     * <p>同一邮箱同一类型 60 秒内不可重复发送，验证码 5 分钟有效。</p>
     */
    @PostMapping("/auth/send-code")
    public Result<String> sendVerificationCode(@Valid @RequestBody SendCodeRequest request) {
        String type = request.getType();
        if (!"login".equals(type) && !"register".equals(type)) {
            return Result.fail(400, "type 只能为 login 或 register");
        }
        // 登录验证码：邮箱必须已注册
        if ("login".equals(type) && userService.findByEmail(request.getEmail()) == null) {
            return Result.fail(404, "该邮箱未注册");
        }
        // 注册验证码：邮箱必须未注册
        if ("register".equals(type) && userService.isEmailTaken(request.getEmail())) {
            return Result.fail(409, "该邮箱已被注册");
        }
        try {
            String code = verificationCodeService.generateCode(request.getEmail(), type);
            emailService.sendVerificationCode(request.getEmail(), code, type);
            log.info("验证码已发送: email={}, type={}", request.getEmail(), type);
            return Result.ok("验证码已发送");
        } catch (RuntimeException e) {
            return Result.fail(429, e.getMessage());
        }
    }

    /**
     * 验证码登录——使用邮箱 + 6位验证码登录。
     *
     * <p>验证通过后签发 JWT，同时重置登录失败计数。</p>
     */
    @PostMapping("/auth/login-by-code")
    public Result<LoginResponse> loginByCode(@Valid @RequestBody LoginByCodeRequest request) {
        // 校验验证码
        if (!verificationCodeService.verifyCode(request.getEmail(), "login", request.getCode())) {
            return Result.fail(401, "验证码错误或已过期");
        }
        // 查找用户
        User user = userService.findByEmail(request.getEmail());
        if (user == null) {
            return Result.fail(404, "该邮箱未注册");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            return Result.fail(403, "账户未激活，请先完成邮箱验证");
        }
        // 重置失败计数，签发 JWT
        userService.recordLoginSuccess(user.getUsername());
        String token = jwtUtil.generateToken(user.getUsername());
        List<String> roles = userService.findRoleCodesByUserId(user.getId());
        log.info("验证码登录成功: email={}", request.getEmail());
        return Result.ok(new LoginResponse(
                token, user.getId(), user.getUsername(), user.getEmail(), roles));
    }

    /**
     * 验证邮箱——注册后调用，激活账户。
     *
     * <p>验证成功后 email_verified=1, status=1，用户可正常登录。</p>
     */
    @PostMapping("/auth/verify-email")
    public Result<String> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        if (!verificationCodeService.verifyCode(request.getEmail(), "register", request.getCode())) {
            return Result.fail(400, "验证码错误或已过期");
        }
        userService.verifyEmail(request.getEmail());
        return Result.ok("邮箱验证成功，账户已激活");
    }

    // ========================================================================
    // 认证接口 —— /api/user/**
    // ========================================================================

    /**
     * 获取当前用户的个人信息。
     *
     * @throws BusinessException(404) 若用户数据异常（如已被管理员删除但 Token 仍有效）
     */
    @GetMapping("/user/profile")
    public Result<UserProfileResponse> getProfile() {
        UserDetailsImpl principal = getCurrentUser();
        User user = userService.findById(principal.getId());
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        return Result.ok(UserProfileResponse.from(user));
    }

    /**
     * 修改个人信息——仅更新传入的非空字段（邮箱/手机号）。
     */
    @PutMapping("/user/profile")
    public Result<String> updateProfile(@Valid @RequestBody UserProfileUpdateRequest request) {
        UserDetailsImpl principal = getCurrentUser();
        userService.updateProfile(principal.getId(), request.getEmail(), request.getPhone());
        log.info("用户信息更新成功: {}", principal.getId());
        return Result.ok("修改成功");
    }

    /**
     * 修改密码——需提供旧密码验证身份。
     */
    @PutMapping("/user/password")
    public Result<String> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        UserDetailsImpl principal = getCurrentUser();
        userService.changePassword(principal.getId(),
                request.getOldPassword(), request.getNewPassword());
        log.info("用户密码修改成功: {}", principal.getId());
        return Result.ok("密码修改成功");
    }

    // ========================================================================
    // 管理员接口 —— /api/admin/**  (需 SUPER_ADMIN 角色)
    // ========================================================================

    /**
     * 获取全部用户列表（不含密码）。
     */
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @GetMapping("/admin/users")
    public Result<List<UserProfileResponse>> listUsers() {
        List<UserProfileResponse> list = userService.findAll().stream()
                .map(UserProfileResponse::from)
                .collect(Collectors.toList());
        return Result.ok(list);
    }

    /**
     * 启用/禁用用户。status: 0-禁用, 1-启用。
     */
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PutMapping("/admin/user/{id}/status")
    public Result<String> toggleUserStatus(@PathVariable Long id,
                                           @Valid @RequestBody StatusRequest request) {
        userService.updateStatus(id, request.getStatus());
        return Result.ok("状态更新成功");
    }

    /**
     * 删除用户——不允许删除自己。
     */
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @DeleteMapping("/admin/user/{id}")
    public Result<String> deleteUser(@PathVariable Long id) {
        UserDetailsImpl principal = getCurrentUser();
        if (principal.getId().equals(id)) {
            return Result.fail(400, "不能删除自己的账户");
        }
        userService.deleteUser(id);
        return Result.ok("删除成功");
    }

    // ========================================================================
    // 工具方法
    // ========================================================================

    /** 从 SecurityContext 获取当前登录用户 */
    private UserDetailsImpl getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (UserDetailsImpl) authentication.getPrincipal();
    }
}
