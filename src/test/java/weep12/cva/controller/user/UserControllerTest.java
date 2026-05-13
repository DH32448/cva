package weep12.cva.controller.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import weep12.cva.domain.user.dto.ChangePasswordRequest;
import weep12.cva.domain.user.dto.LoginByCodeRequest;
import weep12.cva.domain.user.dto.LoginRequest;
import weep12.cva.domain.user.dto.RegisterRequest;
import weep12.cva.domain.user.dto.SendCodeRequest;
import weep12.cva.domain.user.dto.UserProfileUpdateRequest;
import weep12.cva.domain.user.dto.VerifyEmailRequest;
import weep12.cva.domain.user.entity.User;
import weep12.cva.exception.BusinessException;
import weep12.cva.exception.GlobalExceptionHandler;
import weep12.cva.security.UserDetailsImpl;
import weep12.cva.service.mail.EmailService;
import weep12.cva.service.mail.VerificationCodeService;
import weep12.cva.service.user.UserService;
import weep12.cva.util.JwtUtil;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private UserService userService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private EmailService emailService;
    @Mock
    private VerificationCodeService verificationCodeService;

    @InjectMocks
    private UserController userController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private UserDetailsImpl buildPrincipal(Long id, String username, String... roles) {
        List<SimpleGrantedAuthority> authorities = roles.length > 0
                ? List.of(roles).stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList()
                : Collections.emptyList();
        return new UserDetailsImpl(id, username, "pw", "t@t.com", true, false, authorities);
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(userController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        SecurityContextHolder.clearContext();
    }

    // ==================== Login ====================

    @Test
    void login_ShouldReturnToken_WhenValid() throws Exception {
        UserDetailsImpl userDetails = buildPrincipal(1L, "testuser", "USER");
        Authentication auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(jwtUtil.generateToken("testuser")).thenReturn("jwt-token");

        LoginRequest req = new LoginRequest();
        req.setUsername("testuser");
        req.setPassword("pass");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").value("jwt-token"))
                .andExpect(jsonPath("$.data.roles[0]").value("USER"));
        verify(userService).recordLoginSuccess("testuser");
    }

    @Test
    void login_ShouldReturn400_WhenUsernameBlank() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername("");
        req.setPassword("pass");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void login_ShouldReturn401_WhenBadCredentials() throws Exception {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));
        LoginRequest req = new LoginRequest();
        req.setUsername("x");
        req.setPassword("x");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
        verify(userService).recordLoginFailure("x");
    }

    // ==================== Register ====================

    @Test
    void register_ShouldSucceed_WhenValid() throws Exception {
        when(passwordEncoder.encode("password123")).thenReturn("enc");
        when(userService.register(any(User.class))).thenReturn(1L);

        RegisterRequest req = new RegisterRequest();
        req.setUsername("newuser");
        req.setPassword("password123");
        req.setEmail("new@example.com");
        req.setPhone("13800000000");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("注册成功"));
    }

    @Test
    void register_ShouldReturn400_WhenUsernameBlank() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("");
        req.setPassword("password123");
        req.setEmail("new@example.com");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void register_ShouldReturn400_WhenPasswordTooShort() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("newuser");
        req.setPassword("12345");
        req.setEmail("new@example.com");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void register_ShouldReturn400_WhenEmailInvalid() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("newuser");
        req.setPassword("password123");
        req.setEmail("bad-email");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void register_ShouldReturn409_WhenUsernameTaken() throws Exception {
        when(passwordEncoder.encode("password123")).thenReturn("enc");
        when(userService.register(any(User.class))).thenThrow(new BusinessException(409, "用户名已被占用"));

        RegisterRequest req = new RegisterRequest();
        req.setUsername("taken");
        req.setPassword("password123");
        req.setEmail("t@t.com");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("用户名已被占用"));
    }

    // ==================== GetProfile ====================

    @Test
    void getProfile_ShouldReturnProfile() throws Exception {
        UserDetailsImpl principal = buildPrincipal(1L, "testuser");
        setAuthentication(principal);

        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setEmail("t@t.com");
        user.setPhone("13800000000");
        user.setStatus(1);
        user.setCreatedAt(LocalDateTime.now());
        when(userService.findById(1L)).thenReturn(user);

        mockMvc.perform(get("/api/user/profile")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("testuser"));
    }

    @Test
    void getProfile_ShouldReturn404_WhenUserNotFound() throws Exception {
        UserDetailsImpl principal = buildPrincipal(999L, "ghost");
        setAuthentication(principal);
        when(userService.findById(999L)).thenReturn(null);

        mockMvc.perform(get("/api/user/profile")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(404));
    }

    // ==================== UpdateProfile ====================

    @Test
    void updateProfile_ShouldSucceed() throws Exception {
        UserDetailsImpl principal = buildPrincipal(1L, "testuser");
        setAuthentication(principal);

        UserProfileUpdateRequest req = new UserProfileUpdateRequest();
        req.setEmail("new@example.com");
        req.setPhone("13900000000");

        mockMvc.perform(put("/api/user/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("修改成功"));
        verify(userService).updateProfile(eq(1L), eq("new@example.com"), eq("13900000000"));
    }

    // ==================== ChangePassword ====================

    @Test
    void changePassword_ShouldSucceed() throws Exception {
        UserDetailsImpl principal = buildPrincipal(1L, "testuser");
        setAuthentication(principal);

        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setOldPassword("old");
        req.setNewPassword("newpass");

        mockMvc.perform(put("/api/user/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("密码修改成功"));
    }

    @Test
    void changePassword_ShouldReturn400_WhenNewPasswordTooShort() throws Exception {
        UserDetailsImpl principal = buildPrincipal(1L, "testuser");
        setAuthentication(principal);

        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setOldPassword("old");
        req.setNewPassword("12345");

        mockMvc.perform(put("/api/user/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(jsonPath("$.code").value(400));
    }

    // ==================== Logout ====================

    @Test
    void logout_ShouldSucceed() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer some-token")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("已登出"));
    }

    // ==================== Refresh ====================

    @Test
    void refreshToken_ShouldReturn401_WhenNoToken() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(401));
    }

    // ==================== SendCode ====================

    @Test
    void sendCode_ShouldSendForLogin_WhenEmailExists() throws Exception {
        SendCodeRequest req = new SendCodeRequest();
        req.setEmail("t@t.com");
        req.setType("login");
        when(userService.findByEmail("t@t.com")).thenReturn(new User());
        when(verificationCodeService.generateCode("t@t.com", "login")).thenReturn("123456");

        mockMvc.perform(post("/api/auth/send-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("验证码已发送"));
    }

    @Test
    void sendCode_ShouldReturn404_WhenEmailNotRegisteredForLogin() throws Exception {
        SendCodeRequest req = new SendCodeRequest();
        req.setEmail("unknown@t.com");
        req.setType("login");
        when(userService.findByEmail("unknown@t.com")).thenReturn(null);

        mockMvc.perform(post("/api/auth/send-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void sendCode_ShouldReturn409_WhenEmailTakenForRegister() throws Exception {
        SendCodeRequest req = new SendCodeRequest();
        req.setEmail("taken@t.com");
        req.setType("register");
        when(userService.isEmailTaken("taken@t.com")).thenReturn(true);

        mockMvc.perform(post("/api/auth/send-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(jsonPath("$.code").value(409));
    }

    // ==================== LoginByCode ====================

    @Test
    void loginByCode_ShouldReturnToken_WhenValid() throws Exception {
        LoginByCodeRequest req = new LoginByCodeRequest();
        req.setEmail("t@t.com");
        req.setCode("123456");
        when(verificationCodeService.verifyCode("t@t.com", "login", "123456")).thenReturn(true);
        User user = buildUser(1L, "testuser", "t@t.com");
        when(userService.findByEmail("t@t.com")).thenReturn(user);
        when(jwtUtil.generateToken("testuser")).thenReturn("jwt-token");
        when(userService.findRoleCodesByUserId(1L)).thenReturn(Collections.emptyList());

        mockMvc.perform(post("/api/auth/login-by-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").value("jwt-token"));
    }

    @Test
    void loginByCode_ShouldReturn401_WhenCodeInvalid() throws Exception {
        LoginByCodeRequest req = new LoginByCodeRequest();
        req.setEmail("t@t.com");
        req.setCode("wrong");
        when(verificationCodeService.verifyCode("t@t.com", "login", "wrong")).thenReturn(false);

        mockMvc.perform(post("/api/auth/login-by-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(jsonPath("$.code").value(401));
    }

    // ==================== VerifyEmail ====================

    @Test
    void verifyEmail_ShouldSucceed() throws Exception {
        VerifyEmailRequest req = new VerifyEmailRequest();
        req.setEmail("new@t.com");
        req.setCode("654321");
        when(verificationCodeService.verifyCode("new@t.com", "register", "654321")).thenReturn(true);

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(jsonPath("$.code").value(200));
    }

    // ==================== helpers ====================

    private User buildUser(Long id, String username, String email) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(email);
        user.setStatus(1);
        return user;
    }

    private void setAuthentication(UserDetailsImpl principal) {
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
