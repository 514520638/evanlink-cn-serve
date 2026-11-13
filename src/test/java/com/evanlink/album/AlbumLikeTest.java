package com.evanlink.album;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.*;
import org.springframework.web.server.ResponseStatusException;
import com.evanlink.model.AlbumPhoto;
import com.evanlink.repository.AlbumPhotoRepository;
import com.evanlink.netdisk.BaiduNetdiskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Proxy;
import java.util.*;
import jakarta.servlet.http.Cookie;

class AlbumLikeTest {
    private AlbumLikeController fixture() {
        var env=new MockEnvironment().withProperty("APP_ALBUM_LIKE_SECRET","test-only-like-key");
        var rows=new HashMap<String,AlbumLike>();
        var likes=(AlbumLikeRepository)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{AlbumLikeRepository.class},(p,m,a)->switch(m.getName()) {
            case "existsById" -> rows.containsKey(a[0]);
            case "countByPhotoId" -> rows.values().stream().filter(r->r.photoId.equals(a[0])).count();
            case "saveAndFlush" -> {var row=(AlbumLike)a[0]; rows.put(row.id,row);yield row;}
            default -> throw new AssertionError(m.getName());
        });
        var photo=new AlbumPhoto();photo.setId(1L);
        var photos=(AlbumPhotoRepository)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{AlbumPhotoRepository.class},(p,m,a)->Optional.of(photo));
        return new AlbumLikeController(likes,photos,new BaiduNetdiskService(env,null,null,new ObjectMapper()),env);
    }
    private MockHttpServletRequest request() {var req=new MockHttpServletRequest();req.addHeader("X-Album-Request","1");return req;}
    @Test void repeatedClicksAreIdempotent() {
        var controller=fixture();var response=new MockHttpServletResponse();controller.state(1L,request(),response);
        String cookie=response.getHeader("Set-Cookie").split(";",2)[0].split("=",2)[1];
        var request=request();request.setCookies(new Cookie("evan_album_visitor",cookie));
        for(int i=0;i<50;i++){var result=controller.like(1L,request,new MockHttpServletResponse());assertEquals(1L,result.get("count"));assertEquals(true,result.get("liked"));}
    }
    @Test void changingCookiesCannotBypassIpLimit() {
        var controller=fixture();for(int i=0;i<20;i++)controller.like(1L,request(),new MockHttpServletResponse());
        var error=assertThrows(ResponseStatusException.class,()->controller.like(1L,request(),new MockHttpServletResponse()));assertEquals(429,error.getStatusCode().value());
    }
    @Test void crossSiteRequestCannotWriteLikes() {
        var req=request();req.addHeader("Sec-Fetch-Site","cross-site");
        assertEquals(403,assertThrows(ResponseStatusException.class,()->fixture().like(1L,req,new MockHttpServletResponse())).getStatusCode().value());
    }
}
