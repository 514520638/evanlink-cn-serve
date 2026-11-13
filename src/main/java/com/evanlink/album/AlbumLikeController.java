package com.evanlink.album;
import com.evanlink.repository.AlbumPhotoRepository;
import com.evanlink.netdisk.BaiduNetdiskService;
import jakarta.servlet.http.*;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** 匿名点赞：签名访客 Cookie、数据库唯一约束、入口限流共同防止重复写入。 */
@RestController
@RequestMapping("/api/album/photos/{photoId}/like")
public class AlbumLikeController {
    private static final String COOKIE="evan_album_visitor";
    private final AlbumLikeRepository likes;
    private final AlbumPhotoRepository photos;
    private final BaiduNetdiskService disk;
    private final Environment env;
    private final Map<String,long[]> limits=new HashMap<>();
    public AlbumLikeController(AlbumLikeRepository likes,AlbumPhotoRepository photos,BaiduNetdiskService disk,Environment env){this.likes=likes;this.photos=photos;this.disk=disk;this.env=env;}
    private String sign(String value) {
        String secret=env.getProperty("APP_ALBUM_LIKE_SECRET",env.getProperty("app.admin.password",""));
        if(secret.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"点赞服务尚未配置");
        try {var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal(("album-like:"+value).getBytes(StandardCharsets.UTF_8)));}
        catch(Exception ex){throw new IllegalStateException("Like identity signing failed");}
    }
    private String visitor(HttpServletRequest request,HttpServletResponse response){
        String value=request.getCookies()==null?null:Arrays.stream(request.getCookies()).filter(c->COOKIE.equals(c.getName())).map(Cookie::getValue).findFirst().orElse(null);
        if(value!=null && value.matches("[a-f0-9-]{36}\\.[a-f0-9]{64}")) {String[] parts=value.split("\\.");if(java.security.MessageDigest.isEqual(sign(parts[0]).getBytes(StandardCharsets.UTF_8),parts[1].getBytes(StandardCharsets.UTF_8)))return sign(parts[0]);}
        String id=UUID.randomUUID().toString();String digest=sign(id);
        response.addHeader("Set-Cookie",ResponseCookie.from(COOKIE,id+"."+digest).httpOnly(true).secure(request.isSecure()||"https".equals(request.getHeader("X-Forwarded-Proto"))).sameSite("Lax").path("/").maxAge(365L*24*3600).build().toString());return digest;
    }
    private void checkPhoto(Long id){if(photos.findByIdAndDeletedFalse(id).filter(p->p.isPublished()).isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"照片不存在");}
    private Map<String,Object> result(Long photo,String visitor){return Map.of("count",likes.countByPhotoId(photo),"liked",likes.existsById(photo+":"+visitor));}
    @GetMapping public Map<String,Object> state(@PathVariable Long photoId,HttpServletRequest request,HttpServletResponse response){checkPhoto(photoId);response.setHeader("Cache-Control","no-store");return result(photoId,visitor(request,response));}
    @PostMapping public synchronized Map<String,Object> like(@PathVariable Long photoId,HttpServletRequest request,HttpServletResponse response){
        if(!"1".equals(request.getHeader("X-Album-Request"))||"cross-site".equals(request.getHeader("Sec-Fetch-Site"))||!disk.acceptsOrigin(request.getHeader("Origin")))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"请求来源无效");
        checkPhoto(photoId);String visitor=visitor(request,response);String id=photoId+":"+visitor;
        if(likes.existsById(id))return result(photoId,visitor);
        String ip=request.getRemoteAddr();if(Set.of("127.0.0.1","0:0:0:0:0:0:0:1","::1").contains(ip)&&request.getHeader("X-Real-IP")!=null)ip=request.getHeader("X-Real-IP");
        long now=System.currentTimeMillis();limits.entrySet().removeIf(e->e.getValue()[0]<now);
        if(!limits.containsKey(ip) && limits.size()>=5000)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"点赞服务繁忙，请稍后重试");
        long[] window=limits.computeIfAbsent(ip,k->new long[]{now+60000,0});
        if(limits.size()>5000 || ++window[1]>20)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"点赞过于频繁，请稍后再试");
        var row=new AlbumLike();row.id=id;row.photoId=photoId;row.visitorHash=visitor;
        try{likes.saveAndFlush(row);}catch(org.springframework.dao.DataIntegrityViolationException duplicate){if(!likes.existsById(id))throw duplicate;}
        return result(photoId,visitor);
    }
    @ExceptionHandler(ResponseStatusException.class) public ResponseEntity<Map<String,String>> error(ResponseStatusException ex){return ResponseEntity.status(ex.getStatusCode()).header("Cache-Control","no-store").body(Map.of("message",Objects.requireNonNullElse(ex.getReason(),"点赞失败")));}
}
