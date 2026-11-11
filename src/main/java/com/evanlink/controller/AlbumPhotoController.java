package com.evanlink.controller;

import com.evanlink.dto.AlbumPhotoResponse;
import com.evanlink.service.AdminAuthService;
import com.evanlink.service.AlbumPhotoService;
import com.evanlink.netdisk.BaiduNetdiskService;
import jakarta.servlet.http.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.util.concurrent.Semaphore;

@RestController
@RequestMapping("/api/album/photos")
public class AlbumPhotoController {
    private final AlbumPhotoService service;
    private final AdminAuthService auth;
    private final BaiduNetdiskService disk;
    private final Semaphore uploadSlot=new Semaphore(1);
    public AlbumPhotoController(AlbumPhotoService service,AdminAuthService auth,BaiduNetdiskService disk) {this.service=service;this.auth=auth;this.disk=disk;}
    private boolean owner(HttpServletRequest req) {
        if(!disk.acceptsOrigin(req.getHeader("Origin"))) return false;
        return auth.isValidAuthorization(req.getHeader("Authorization")) || (req.getCookies()!=null && Arrays.stream(req.getCookies()).anyMatch(c->AdminAuthService.ADMIN_COOKIE_NAME.equals(c.getName()) && auth.isValidToken(c.getValue())));
    }
    private void requireOwner(HttpServletRequest req) {
        if(!owner(req)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"请先登录后再管理相册");
        if(!"1".equals(req.getHeader("X-Netdisk-Request")) || "cross-site".equals(req.getHeader("Sec-Fetch-Site"))) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"请求来源无效");
    }
    @GetMapping public ResponseEntity<List<AlbumPhotoResponse>> list(HttpServletRequest req) {return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.list());}
    // 原始二进制请求体避免 Servlet multipart 解析器自动落盘；一次只处理一张。
    @PostMapping(consumes="application/octet-stream")
    public AlbumPhotoResponse upload(HttpServletRequest req,@RequestParam String name,@RequestParam(required=false) String title,@RequestParam(required=false) String description) throws java.io.IOException {
        requireOwner(req);
        int limit=20*1024*1024;
        if(req.getContentLengthLong()>limit) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"单张照片最多 20MB");
        if(!uploadSlot.tryAcquire()) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"正在上传另一张照片，请稍后重试");
        try {
            byte[] bytes=req.getInputStream().readNBytes(limit+1);
            if(bytes.length>limit) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"单张照片最多 20MB");
            return service.upload(bytes,name,title,description);
        } finally {uploadSlot.release();}
    }
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(HttpServletRequest req,@PathVariable Long id) {requireOwner(req);service.delete(id);return ResponseEntity.noContent().build();}
    @ExceptionHandler(ResponseStatusException.class) public ResponseEntity<Map<String,String>> error(ResponseStatusException ex) {return ResponseEntity.status(ex.getStatusCode()).body(Map.of("message",Objects.requireNonNullElse(ex.getReason(),"相册请求失败")));}
    @ExceptionHandler(NoSuchElementException.class) public ResponseEntity<Map<String,String>> missing() {return ResponseEntity.status(404).body(Map.of("message","照片不存在"));}
}
