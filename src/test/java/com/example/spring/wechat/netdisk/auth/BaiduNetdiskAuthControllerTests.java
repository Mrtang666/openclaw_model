package com.example.spring.wechat.netdisk.auth;

import com.example.spring.wechat.netdisk.model.NetdiskAuthCallbackResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.TestPropertySource;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BaiduNetdiskAuthController.class)
@TestPropertySource(properties = "baidu.netdisk.enabled=true")
class BaiduNetdiskAuthControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BaiduNetdiskAuthService authService;

    @Test
    void callbackCompletesAuthorizationAndShowsSuccessPage() throws Exception {
        when(authService.completeAuthorization("state-1", "code-1"))
                .thenReturn(new NetdiskAuthCallbackResult(true, "wx-user-1", null, "百度网盘授权成功"));

        mockMvc.perform(get("/api/netdisk/baidu/callback")
                        .param("state", "state-1")
                        .param("code", "code-1"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("百度网盘授权成功")));

        verify(authService).completeAuthorization("state-1", "code-1");
    }

    @Test
    void callbackShowsProviderErrorWhenBaiduReturnsError() throws Exception {
        mockMvc.perform(get("/api/netdisk/baidu/callback")
                        .param("state", "state-1")
                        .param("error", "access_denied")
                        .param("error_description", "user denied"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("百度网盘授权失败")));
    }
}
