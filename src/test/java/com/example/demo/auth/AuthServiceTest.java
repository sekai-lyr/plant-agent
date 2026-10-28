package com.example.demo.auth;

import com.example.demo.chat.repository.mysql.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Test
    void registerRejectsMissingCredentialsWithoutQueryingRepository() {
        AuthService service = new AuthService(userRepository);

        var result = service.register("", null, null, null);

        assertEquals(500, result.get("code"));
        assertEquals("用户名和密码不能为空", result.get("message"));
        verify(userRepository, never()).existsByUserName(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void loginRejectsMissingCredentialsWithoutQueryingRepository() {
        AuthService service = new AuthService(userRepository);

        var result = service.login(null, "");

        assertEquals(500, result.get("code"));
        assertEquals("用户名或密码错误", result.get("message"));
        verify(userRepository, never()).findByUserName(org.mockito.ArgumentMatchers.any());
    }
}
