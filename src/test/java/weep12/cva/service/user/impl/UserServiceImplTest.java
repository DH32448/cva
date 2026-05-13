package weep12.cva.service.user.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import weep12.cva.domain.user.entity.User;
import weep12.cva.exception.BusinessException;
import weep12.cva.mapper.user.UserMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private PasswordEncoder passwordEncoder;

    private UserServiceImpl userService;
    private User testUser;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(userMapper, passwordEncoder);
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setPassword("encodedPassword");
        testUser.setEmail("test@example.com");
        testUser.setPhone("13800000000");
    }

    @Test
    void findById_ShouldReturnUser_WhenExists() {
        when(userMapper.findById(1L)).thenReturn(testUser);
        assertEquals("testuser", userService.findById(1L).getUsername());
    }

    @Test
    void findById_ShouldReturnNull_WhenNotExists() {
        when(userMapper.findById(999L)).thenReturn(null);
        assertNull(userService.findById(999L));
    }

    @Test
    void register_ShouldSucceed_WhenUsernameNotTaken() {
        when(userMapper.findByUsername("testuser")).thenReturn(null);
        doAnswer(inv -> { inv.<User>getArgument(0).setId(100L); return 1; })
                .when(userMapper).insert(any(User.class));
        assertEquals(100L, userService.register(testUser));
    }

    @Test
    void register_ShouldThrow_WhenUsernameTaken() {
        when(userMapper.findByUsername("testuser")).thenReturn(testUser);
        BusinessException ex = assertThrows(BusinessException.class, () -> userService.register(testUser));
        assertEquals(409, ex.getCode());
        verify(userMapper, never()).insert(any());
    }

    @Test
    void register_ShouldThrow_WhenEmailTaken() {
        when(userMapper.findByUsername("testuser")).thenReturn(null);
        User other = new User(); other.setId(2L);
        when(userMapper.findByEmail("test@example.com")).thenReturn(other);
        BusinessException ex = assertThrows(BusinessException.class, () -> userService.register(testUser));
        assertEquals(409, ex.getCode());
        assertTrue(ex.getMessage().contains("邮箱"));
    }

    @Test
    void updateProfile_ShouldSucceed() {
        when(userMapper.updateProfile(1L, "new@example.com", "13900000000")).thenReturn(1);
        assertDoesNotThrow(() -> userService.updateProfile(1L, "new@example.com", "13900000000"));
    }

    @Test
    void updateProfile_ShouldThrow_WhenEmailTakenByOther() {
        User other = new User(); other.setId(2L);
        when(userMapper.findByEmail("new@example.com")).thenReturn(other);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.updateProfile(1L, "new@example.com", null));
        assertEquals(409, ex.getCode());
    }

    @Test
    void updateProfile_ShouldThrow_WhenUserNotExists() {
        when(userMapper.updateProfile(999L, "new@example.com", "13900000000")).thenReturn(0);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.updateProfile(999L, "new@example.com", "13900000000"));
        assertEquals(404, ex.getCode());
    }

    @Test
    void changePassword_ShouldSucceed() {
        when(userMapper.findById(1L)).thenReturn(testUser);
        when(passwordEncoder.matches("old", "encodedPassword")).thenReturn(true);
        when(passwordEncoder.encode("newpass")).thenReturn("encodedNew");

        userService.changePassword(1L, "old", "newpass");
        verify(userMapper).updatePassword(1L, "encodedNew");
    }

    @Test
    void changePassword_ShouldThrow_WhenOldPasswordWrong() {
        when(userMapper.findById(1L)).thenReturn(testUser);
        when(passwordEncoder.matches("wrong", "encodedPassword")).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.changePassword(1L, "wrong", "newpass"));
        assertEquals(400, ex.getCode());
    }

    @Test
    void isEmailTaken_ShouldReturnTrue_WhenExists() {
        when(userMapper.findByEmail("taken@t.com")).thenReturn(testUser);
        assertTrue(userService.isEmailTaken("taken@t.com"));
    }

    @Test
    void isEmailTaken_ShouldReturnFalse_WhenNotExists() {
        when(userMapper.findByEmail("free@t.com")).thenReturn(null);
        assertFalse(userService.isEmailTaken("free@t.com"));
    }

    @Test
    void findAll_ShouldReturnList() {
        when(userMapper.findAll()).thenReturn(List.of(testUser));
        assertEquals(1, userService.findAll().size());
    }

    @Test
    void updateStatus_ShouldSucceed() {
        when(userMapper.findById(1L)).thenReturn(testUser);
        userService.updateStatus(1L, 0);
        verify(userMapper).updateStatus(1L, 0);
    }

    @Test
    void deleteUser_ShouldSucceed() {
        when(userMapper.findById(1L)).thenReturn(testUser);
        userService.deleteUser(1L);
        verify(userMapper).deleteById(1L);
    }

    @Test
    void recordLoginFailure_ShouldIncrementAndLock() {
        testUser.setLoginFailures(4);
        when(userMapper.findByUsername("testuser")).thenReturn(testUser);
        userService.recordLoginFailure("testuser");
        verify(userMapper).incrementLoginFailures(1L);
        verify(userMapper).lockAccount(eq(1L), any());
    }

    @Test
    void recordLoginSuccess_ShouldReset() {
        when(userMapper.findByUsername("testuser")).thenReturn(testUser);
        userService.recordLoginSuccess("testuser");
        verify(userMapper).resetLoginFailures(1L);
    }
}
