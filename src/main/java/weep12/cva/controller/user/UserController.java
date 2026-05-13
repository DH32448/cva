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
import weep12.cva.domain.user.dto.ChangePasswordRequest;
import weep12.cva.domain.user.dto.LoginRequest;
import weep12.cva.domain.user.dto.RegisterRequest;
import weep12.cva.domain.user.dto.StatusRequest;
import weep12.cva.domain.user.dto.UserProfileUpdateRequest;
import weep12.cva.domain.user.entity.User;
import weep12.cva.domain.user.vo.LoginResponse;
import weep12.cva.domain.user.vo.UserProfileResponse;
import weep12.cva.exception.BusinessException;
import weep12.cva.result.Result;
import weep12.cva.security.UserDetailsImpl;
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
 *   <li>{@code /api/user/** }    —— 需认证：个人信息查看/修改、密码修改</li>
 *   <li>{@code /api/admin/** }   —— 需 SUPER_ADMIN 角色：用户管理</li>
 * </ul></p>
 */
@Slf4j
@RestController
@RequestMapping("/api")
public class UserController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    public UserController(AuthenticationManager authenticationManager,
                          JwtUtil jwtUtil,
                          UserService userService,
                          PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
    }

    // ========================================================================
    // 公开接口 —— /api/auth/**
    // ========================================================================

    /**
     * 用户登录——验证用户名/密码，返回 JWT Token。
     */
    @PostMapping("/auth/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(), request.getPassword()));
            UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

            userService.recordLoginSuccess(userDetails.getUsername());

            String token = jwtUtil.generateToken(userDetails.getUsername());
            List<String> roles = userDetails.getAuthorities().stream()
                    .map(a -> a.getAuthority().replace("ROLE_", ""))
                    .collect(Collectors.toList());

            log.info("用户登录成功: {}", userDetails.getUsername());
            return Result.ok(new LoginResponse(
                    token, userDetails.getId(),
                    userDetails.getUsername(), roles));
        } catch (BadCredentialsException e) {
            userService.recordLoginFailure(request.getUsername());
            throw e;
        }
    }

    /**
     * 用户注册——创建新账户，默认状态为启用。
     */
    @PostMapping("/auth/register")
    public Result<String> register(@Valid @RequestBody RegisterRequest request) {
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
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
            jwtUtil.blacklistToken(token);
        }
        SecurityContextHolder.clearContext();
        return Result.ok("已登出");
    }

    /**
     * Token 刷新——旧 Token 加入黑名单，签发新 Token。
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
                principal.getUsername(), null));
    }

    // ========================================================================
    // 认证接口 —— /api/user/**
    // ========================================================================

    /**
     * 获取当前用户的个人信息。
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
     * 修改个人信息——仅更新传入的非空字段（手机号）。
     */
    @PutMapping("/user/profile")
    public Result<String> updateProfile(@Valid @RequestBody UserProfileUpdateRequest request) {
        UserDetailsImpl principal = getCurrentUser();
        userService.updateProfile(principal.getId(), request.getPhone());
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

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @GetMapping("/admin/users")
    public Result<List<UserProfileResponse>> listUsers() {
        List<UserProfileResponse> list = userService.findAll().stream()
                .map(UserProfileResponse::from)
                .collect(Collectors.toList());
        return Result.ok(list);
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PutMapping("/admin/user/{id}/status")
    public Result<String> toggleUserStatus(@PathVariable Long id,
                                           @Valid @RequestBody StatusRequest request) {
        userService.updateStatus(id, request.getStatus());
        return Result.ok("状态更新成功");
    }

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
