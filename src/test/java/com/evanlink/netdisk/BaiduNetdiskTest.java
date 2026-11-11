package com.evanlink.netdisk;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.*;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.evanlink.service.AdminAuthService;
import jakarta.servlet.http.Cookie;
import java.util.Base64;

class BaiduNetdiskTest {
    MockEnvironment environment() {
        return new MockEnvironment().withProperty("BAIDU_NETDISK_CLIENT_ID","test-client")
            .withProperty("BAIDU_NETDISK_CLIENT_SECRET","test-secret")
            .withProperty("BAIDU_NETDISK_REDIRECT_URI","https://evanlink.cn/api/album/baidu/callback")
            .withProperty("BAIDU_NETDISK_TOKEN_KEY",Base64.getEncoder().encodeToString(new byte[32]));
    }
    BaiduNetdiskService service() {return new BaiduNetdiskService(environment(),null,null,new ObjectMapper());}
    @Test void encryptedCredentialsAreRandomizedAndAuthenticated() throws Exception {
        var service=service();byte[] plain="private-refresh-token".getBytes();
        String first=service.encrypt(plain),second=service.encrypt(plain);
        assertNotEquals(first,second);assertFalse(first.contains("private-refresh-token"));
        assertArrayEquals(plain,service.decrypt(first));
        String[] parts=first.split("\\.");byte[] tampered=Base64.getDecoder().decode(parts[1]);tampered[0]^=1;
        assertThrows(Exception.class,()->service.decrypt(parts[0]+"."+Base64.getEncoder().encodeToString(tampered)));
    }
    @Test void directoryTraversalAndSiblingDirectoriesAreRejected() {
        var service=service();assertEquals("/evan-blog-album/trip",service.checkedPath("/evan-blog-album/trip"));
        assertThrows(ResponseStatusException.class,()->service.checkedPath("/evan-blog-album/../private"));
        assertThrows(ResponseStatusException.class,()->service.checkedPath("/evan-blog-album-other"));
        assertThrows(ResponseStatusException.class,()->service.checkedPath("relative"));
    }
    @Test void appIdIsRejectedBeforeRedirectingToBaidu() {
        var env=environment().withProperty("BAIDU_NETDISK_CLIENT_ID","123456789");
        var disk=new BaiduNetdiskService(env,null,null,new ObjectMapper());
        var error=assertThrows(ResponseStatusException.class,()->disk.authorize("state"));
        assertTrue(error.getReason().contains("AppKey"));
    }
    @Test void quotedAppKeyIsRejected() {
        var env=environment().withProperty("BAIDU_NETDISK_CLIENT_ID","\"test-client\"");
        var disk=new BaiduNetdiskService(env,null,null,new ObjectMapper());
        assertThrows(ResponseStatusException.class,()->disk.authorize("state"));
    }
    @Test void otherOriginsCannotReadOwnerData() {
        assertTrue(service().acceptsOrigin("https://evanlink.cn"));
        assertFalse(service().acceptsOrigin("https://evanlink.cn.evil.example"));
        assertFalse(service().acceptsOrigin("https://other.evanlink.cn"));
        assertFalse(service().acceptsOrigin("http://evanlink.cn"));
    }
    @Test void authorizationHasStateAndConfiguredCallback() {
        String url=service().authorize("random-state");
        assertTrue(url.contains("state=random-state"));assertTrue(url.contains("response_type=code"));assertFalse(url.contains("test-secret"));
    }
    @Test void anonymousUsersCannotStartAuthorizationOrFetchPrivateImages() {
        var controller=new BaiduNetdiskController(new BaiduNetdiskService(environment(),null,null,new ObjectMapper()) { @Override public boolean published(String id) {return false;} },new AdminAuthService());
        assertThrows(ResponseStatusException.class,()->controller.authorize(new MockHttpServletRequest()));
        assertThrows(ResponseStatusException.class,()->controller.media(new MockHttpServletRequest(),new MockHttpServletResponse(),"123"));
    }
    @Test void callbackRejectsWrongStateAndConsumesIt() throws Exception {
        var auth=new AdminAuthService() { @Override public boolean isValidToken(String token) {return "owner".equals(token);} };
        var controller=new BaiduNetdiskController(service(),auth);
        var request=new MockHttpServletRequest();request.setCookies(new Cookie(AdminAuthService.ADMIN_COOKIE_NAME,"owner"));
        request.getSession().setAttribute("baidu.state","expected");request.getSession().setAttribute("baidu.time",System.currentTimeMillis());
        var response=new MockHttpServletResponse();controller.callback(request,response,"code","wrong");
        assertEquals("/album?baidu=failed",response.getRedirectedUrl());assertNull(request.getSession().getAttribute("baidu.state"));
    }
}
