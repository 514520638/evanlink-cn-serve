package com.evanlink.netdisk;

import com.evanlink.service.AdminAuthService;
import jakarta.servlet.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Semaphore;

/** 网盘管理接口只供博客管理员使用；公开读取限于显式发布的照片。 */
@RestController
@RequestMapping("/api/album/baidu")
public class BaiduNetdiskController {
    private final BaiduNetdiskService disk;
    private final AdminAuthService auth;
    private final Semaphore streams=new Semaphore(3);
    public BaiduNetdiskController(BaiduNetdiskService disk,AdminAuthService auth) {this.disk=disk;this.auth=auth;}
    private boolean owner(HttpServletRequest request) {
        // 既有全局 CORS 较宽，此模块自行拒绝其他站点读取管理员网盘数据。
        if(!disk.acceptsOrigin(request.getHeader("Origin"))) return false;
        if(auth.isValidAuthorization(request.getHeader("Authorization"))) return true;
        return request.getCookies()!=null && Arrays.stream(request.getCookies()).anyMatch(c->AdminAuthService.ADMIN_COOKIE_NAME.equals(c.getName()) && auth.isValidToken(c.getValue()));
    }
    private void requireOwner(HttpServletRequest request) {if(!owner(request)) throw BaiduNetdiskService.fail(401,"请先登录博客管理员");}
    private void mutation(HttpServletRequest request) {
        requireOwner(request);
        if(!"1".equals(request.getHeader("X-Netdisk-Request")) || "cross-site".equals(request.getHeader("Sec-Fetch-Site")) || !disk.acceptsOrigin(request.getHeader("Origin"))) throw BaiduNetdiskService.fail(403,"请求来源无效");
    }
    @GetMapping("/status") public Map<String,Object> status(HttpServletRequest req) {requireOwner(req);return disk.status();}
    @PostMapping("/authorize") public Map<String,String> authorize(HttpServletRequest req) {
        mutation(req);String state=UUID.randomUUID().toString();String url=disk.authorize(state);
        var session=req.getSession(true);session.setAttribute("baidu.state",state);session.setAttribute("baidu.time",System.currentTimeMillis());
        return Map.of("url",url);
    }
    @GetMapping("/callback") public void callback(HttpServletRequest req,HttpServletResponse res,@RequestParam(required=false) String code,@RequestParam(required=false) String state) throws java.io.IOException {
        res.setHeader("Cache-Control","no-store");res.setHeader("Referrer-Policy","no-referrer");
        var session=req.getSession(false);Object expected=null,time=null;
        if(session!=null) synchronized(session) {expected=session.getAttribute("baidu.state");time=session.getAttribute("baidu.time");session.removeAttribute("baidu.state");session.removeAttribute("baidu.time");}
        if(!owner(req)||!(expected instanceof String)||!(time instanceof Long)||state==null||code==null||System.currentTimeMillis()-(Long)time>600000||!MessageDigest.isEqual(((String)expected).getBytes(StandardCharsets.UTF_8),state.getBytes(StandardCharsets.UTF_8))) {res.sendRedirect("/album?baidu=failed");return;}
        try {disk.connect(code);res.sendRedirect("/album?baidu=connected");} catch(ResponseStatusException ex) {res.sendRedirect("/album?baidu=failed");}
    }
    @DeleteMapping("/connection") public Map<String,Boolean> disconnect(HttpServletRequest req) {mutation(req);disk.disconnect();return Map.of("success",true);}
    @GetMapping("/files") public Map<String,Object> files(HttpServletRequest req,@RequestParam(required=false) String path,@RequestParam(defaultValue="1") int page) {requireOwner(req);return disk.files(path==null?disk.root():path,page);}
    public record PublishRequest(List<String> ids) {}
    @PostMapping("/publish") public Map<String,Boolean> publish(HttpServletRequest req,@RequestBody PublishRequest body) {mutation(req);disk.publish(body.ids());return Map.of("success",true);}
    @GetMapping("/media/{id}") public void media(HttpServletRequest req,HttpServletResponse res,@PathVariable String id) throws java.io.IOException {
        if(!owner(req) && !disk.published(id)) throw BaiduNetdiskService.fail(404,"照片不存在或未公开");
        if(!streams.tryAcquire()) throw BaiduNetdiskService.fail(429,"图片加载较多，请稍后重试");
        try {
            var media=disk.media(id);
            res.setContentType(media.contentType());res.setHeader("X-Content-Type-Options","nosniff");res.setHeader("Cache-Control","private, no-store");
            // 小缓冲流式转发，不把整张图片读进 Java 堆，也不落盘。
            try(var in=media.input()) {
                byte[] buffer=new byte[32*1024];long total=0;int count;long deadline=System.nanoTime()+120_000_000_000L;
                while((count=in.read(buffer))!=-1) {total+=count;if(System.nanoTime()>deadline) throw new java.io.IOException("Image stream timed out");if(total>20*1024*1024) throw new java.io.IOException("Image exceeds limit");res.getOutputStream().write(buffer,0,count);}
            }
        } finally {streams.release();}
    }
    @ExceptionHandler(ResponseStatusException.class) public ResponseEntity<Map<String,String>> error(ResponseStatusException ex) {return ResponseEntity.status(ex.getStatusCode()).header("Cache-Control","no-store").body(Map.of("message",Objects.requireNonNullElse(ex.getReason(),"网盘请求失败")));}
}
