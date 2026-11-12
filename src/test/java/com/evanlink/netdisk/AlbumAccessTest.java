package com.evanlink.netdisk;

import com.evanlink.controller.AlbumPhotoController;
import com.evanlink.model.AlbumPhoto;
import com.evanlink.repository.AlbumPhotoRepository;
import com.evanlink.service.AdminAuthService;
import com.evanlink.service.AlbumPhotoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 验证相册的访客读取与管理员写入边界，而不是只检查前端按钮。 */
class AlbumAccessTest {
    private MockMvc fixture() {
        var photo=new AlbumPhoto();photo.setId(1L);photo.setBaiduFileId("123");photo.setUrl("/api/album/baidu/media/123");photo.setTitle("公开照片");
        var repo=(AlbumPhotoRepository)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{AlbumPhotoRepository.class},(p,m,a)->switch(m.getName()) {
            case "findByDeletedFalseOrderBySortOrderAscCreatedAtDesc" -> List.of(photo);
            case "existsByBaiduFileIdAndDeletedFalseAndPublishedTrue" -> "123".equals(a[0]);
            case "findByIdAndDeletedFalse" -> Optional.of(photo);
            case "saveAndFlush" -> a[0];
            default -> throw new AssertionError("Unexpected database operation: "+m.getName());
        });
        // 即便旧环境文件仍配置 false，也不再阻止博客相册公开读取。
        var env=new MockEnvironment().withProperty("BAIDU_NETDISK_PUBLIC_ENABLED","false");
        var disk=new BaiduNetdiskService(env,null,repo,new ObjectMapper()) {
            @Override public Media media(String id) {return new Media(new ByteArrayInputStream(new byte[]{1,2,3}),"image/png");}
            @Override public Uploaded uploadImage(byte[] data,String name) {return new Uploaded("456","/album/test.jpg","image/jpeg");}
            @Override public void deleteOriginal(String id) {throw new AssertionError("Anonymous delete must not reach Baidu");}
        };
        var auth=new AdminAuthService() { @Override public boolean isValidToken(String token) {return "owner".equals(token);} };
        return MockMvcBuilders.standaloneSetup(new AlbumPhotoController(new AlbumPhotoService(repo,disk),auth,disk),new BaiduNetdiskController(disk,auth)).build();
    }
    @Test void guestCanListAlbumWithoutLogin() throws Exception {
        fixture().perform(get("/api/album/photos")).andExpect(status().isOk()).andExpect(jsonPath("$[0].title").value("公开照片"));
    }
    @Test void guestCanReadPublishedImageWithoutLogin() throws Exception {
        fixture().perform(get("/api/album/baidu/media/123")).andExpect(status().isOk()).andExpect(content().contentType("image/png")).andExpect(content().bytes(new byte[]{1,2,3}));
    }
    @Test void guestCannotReadUnpublishedOrDeletedImage() throws Exception {
        fixture().perform(get("/api/album/baidu/media/999")).andExpect(status().isNotFound());
    }
    @Test void guestCannotUploadEvenWithDedicatedRequestHeader() throws Exception {
        fixture().perform(post("/api/album/photos").param("name","test.jpg").header("X-Netdisk-Request","1").contentType("application/octet-stream").content(new byte[]{1})).andExpect(status().isUnauthorized());
    }
    @Test void guestCannotDeleteOrBrowsePrivateNetdiskDirectory() throws Exception {
        var mvc=fixture();
        mvc.perform(delete("/api/album/photos/1").header("X-Netdisk-Request","1")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/album/baidu/files")).andExpect(status().isUnauthorized());
    }
    @Test void loggedInOwnerCanUpload() throws Exception {
        fixture().perform(post("/api/album/photos").param("name","test.jpg").header("X-Netdisk-Request","1").cookie(new Cookie(AdminAuthService.ADMIN_COOKIE_NAME,"owner")).contentType("application/octet-stream").content(new byte[]{1})).andExpect(status().isOk()).andExpect(jsonPath("$.url").value("/api/album/baidu/media/456"));
    }
}
