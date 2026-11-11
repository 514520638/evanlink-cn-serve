package com.evanlink.netdisk;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.evanlink.model.AlbumPhoto;
import com.evanlink.repository.AlbumPhotoRepository;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;
import java.io.InputStream;

/** [POS] 网盘授权、目录访问与发布引用；凭据只在服务端解密。 */
@Service
public class BaiduNetdiskService {
    private final Environment env;
    private final NetdiskCredentialRepository credentials;
    private final AlbumPhotoRepository photos;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER).build();
    public BaiduNetdiskService(Environment env, NetdiskCredentialRepository credentials, AlbumPhotoRepository photos, ObjectMapper json) {
        this.env=env; this.credentials=credentials; this.photos=photos; this.json=json;
    }
    private String setting(String key) { return env.getProperty("BAIDU_NETDISK_"+key, "").trim(); }
    public String root() { String v=setting("ROOT"); return v.isEmpty()?"/evan-blog-album":v; }
    public boolean acceptsOrigin(String origin) {
        if(origin==null) return true; // 非浏览器管理客户端仍需认证及专用请求头。
        try {
            URI actual=URI.create(origin), callback=URI.create(setting("REDIRECT_URI"));
            return Objects.equals(actual.getScheme(),callback.getScheme()) && Objects.equals(actual.getHost(),callback.getHost()) && actual.getPort()==callback.getPort();
        } catch(IllegalArgumentException ex) {return false;}
    }
    public boolean configured() {
        try { return !setting("CLIENT_ID").isEmpty() && !setting("CLIENT_SECRET").isEmpty()
            && !setting("REDIRECT_URI").isEmpty() && Base64.getDecoder().decode(setting("TOKEN_KEY")).length==32; }
        catch (IllegalArgumentException ex) { return false; }
    }
    private void requireConfig() {
        String clientId=setting("CLIENT_ID");
        if(clientId.matches("[0-9]+")) throw fail(503,"BAIDU_NETDISK_CLIENT_ID 似乎填写了 AppID，请改为百度应用页面的 AppKey，并重启 Java 后端");
        if(clientId.startsWith("\"") || clientId.startsWith("'")) throw fail(503,"AppKey 不应包含引号，请修正后端配置并重启");
        if(!configured()) throw fail(503,"百度网盘尚未配置，请先配置后端应用凭据和令牌加密密钥"); }
    public Map<String,Object> status() { return Map.of("configured",configured(),"connected",credentials.existsById(1L),"root",root()); }
    public String authorize(String state) {
        requireConfig();
        return "https://openapi.baidu.com/oauth/2.0/authorize?"+query(Map.of("response_type","code","client_id",setting("CLIENT_ID"),"redirect_uri",setting("REDIRECT_URI"),"scope","basic,netdisk","state",state));
    }
    public synchronized void connect(String code) {
        requireConfig();
        store(oauth(Map.of("grant_type","authorization_code","code",code,"redirect_uri",setting("REDIRECT_URI"))));
    }
    public synchronized void disconnect() { credentials.deleteById(1L); }
    private JsonNode oauth(Map<String,String> params) {
        var p=new HashMap<>(params);p.put("client_id",setting("CLIENT_ID"));p.put("client_secret",setting("CLIENT_SECRET"));
        return get("https://openapi.baidu.com/oauth/2.0/token?"+query(p));
    }
    private void store(JsonNode token) {
        if(token.path("access_token").asText().isBlank() || token.path("refresh_token").asText().isBlank()) throw fail(502,"百度未返回完整授权，请重新连接");
        ((ObjectNode)token).put("expires_at",System.currentTimeMillis()+token.path("expires_in").asLong(0)*1000);
        try {
            var row=new NetdiskCredential();row.encryptedToken=encrypt(json.writeValueAsBytes(token));credentials.save(row);
        } catch(ResponseStatusException ex) { throw ex; } catch(Exception ex) { throw fail(503,"保存网盘授权失败"); }
    }
    private synchronized String accessToken() {
        requireConfig();
        try {
            var row=credentials.findById(1L).orElseThrow(()->fail(409,"请先连接百度网盘"));
            JsonNode token=json.readTree(decrypt(row.encryptedToken));
            if(token.path("expires_at").asLong()<System.currentTimeMillis()+120000) {
                token=oauth(Map.of("grant_type","refresh_token","refresh_token",token.path("refresh_token").asText()));store(token);
            }
            return token.path("access_token").asText();
        } catch(ResponseStatusException ex) { throw ex; } catch(Exception ex) { throw fail(503,"网盘授权无法读取，请检查加密密钥或重新连接"); }
    }
    String encrypt(byte[] plain) throws Exception {
        byte[] iv=new byte[12];new SecureRandom().nextBytes(iv);
        var c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(Base64.getDecoder().decode(setting("TOKEN_KEY")),"AES"),new GCMParameterSpec(128,iv));
        return Base64.getEncoder().encodeToString(iv)+"."+Base64.getEncoder().encodeToString(c.doFinal(plain));
    }
    byte[] decrypt(String encrypted) throws Exception {
        String[] parts=encrypted.split("\\.");var c=Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE,new SecretKeySpec(Base64.getDecoder().decode(setting("TOKEN_KEY")),"AES"),new GCMParameterSpec(128,Base64.getDecoder().decode(parts[0])));
        return c.doFinal(Base64.getDecoder().decode(parts[1]));
    }
    static String query(Map<String,String> params) {
        return params.entrySet().stream().map(e->URLEncoder.encode(e.getKey(),StandardCharsets.UTF_8)+"="+URLEncoder.encode(e.getValue(),StandardCharsets.UTF_8)).collect(java.util.stream.Collectors.joining("&"));
    }
    private JsonNode get(String url) {
        try {
            var response=http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(25)).header("User-Agent","pan.baidu.com").GET().build(),HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()!=200) throw fail(502,"百度接口暂不可用，请稍后重试");
            JsonNode body=json.readTree(response.body());
            if(body.has("error") || body.path("errno").asInt()!=0) throw fail(502,"百度接口拒绝请求，请检查授权、目录和应用权限");
            return body;
        } catch(ResponseStatusException ex) { throw ex; } catch(Exception ex) {
            if(ex instanceof InterruptedException) Thread.currentThread().interrupt();
            // 不将带 access_token 的请求地址或供应商响应输出到日志/客户端。
            throw fail(502,"百度网盘连接失败，请稍后重试");
        }
    }
    protected JsonNode api(String endpoint,Map<String,String> params) {
        var p=new HashMap<>(params);p.put("access_token",accessToken());
        return get("https://pan.baidu.com/rest/2.0/xpan/"+endpoint+"?"+query(p));
    }
    public String checkedPath(String path) {
        String normalized=Path.of(path).normalize().toString();String base=Path.of(root()).normalize().toString();
        if(!normalized.startsWith("/") || !(normalized.equals(base)||normalized.startsWith(base.equals("/")?"/":base+"/"))) throw fail(400,"只能访问配置的相册目录");
        return normalized;
    }
    public Map<String,Object> files(String path,int page) {
        if(page<1 || page>10000) throw fail(400,"页码无效");
        String dir=checkedPath(path);
        JsonNode data=api("file",Map.of("method","list","dir",dir,"start",String.valueOf((page-1)*50),"limit","50","order","name"));
        var result=new ArrayList<Map<String,Object>>();
        for(JsonNode f:data.path("list")) {
            boolean folder=f.path("isdir").asInt()==1;
            String name=f.path("server_filename").asText();
            if(folder || mime(name)!=null) result.add(Map.of("id",f.path("fs_id").asText(),"name",name,"path",f.path("path").asText(),"folder",folder,"size",f.path("size").asLong()));
        }
        return Map.of("items",result,"page",page,"hasMore",data.path("list").size()==50,"path",dir);
    }
    protected JsonNode metadata(String id) {
        if(!id.matches("[0-9]{1,20}")) throw fail(400,"文件标识无效");
        JsonNode data=api("multimedia",Map.of("method","filemetas","fsids","["+id+"]","dlink","1","thumb","1","extra","1"));
        if(data.path("list").isEmpty()) throw fail(404,"网盘照片不存在");
        JsonNode file=data.path("list").get(0);checkedPath(file.path("path").asText());
        if(file.path("isdir").asInt()!=0 || mime(file.path("filename").asText(file.path("server_filename").asText(file.path("path").asText())))==null) throw fail(400,"仅支持 JPG、PNG、WebP、GIF、HEIC、HEIF 照片");
        if(file.path("size").asLong()>20*1024*1024) throw fail(400,"单张照片不能超过 20MB，请先压缩");
        return file;
    }
    public synchronized void publish(List<String> ids) {
        if(ids==null || ids.isEmpty() || ids.size()>20) throw fail(400,"每次请选择 1 至 20 张照片");
        var prepared=new ArrayList<AlbumPhoto>();
        for(String id:new LinkedHashSet<>(ids)) {
            JsonNode f=metadata(id);
            if(photos.existsByBaiduFileIdAndDeletedFalse(id)) continue;
            String name=Path.of(f.path("path").asText()).getFileName().toString();
            var photo=new AlbumPhoto();photo.setBaiduFileId(id);photo.setOriginalName(name);photo.setContentType(mime(name));photo.setFileSize(f.path("size").asLong());photo.setTitle(name);photo.setUrl("/api/album/baidu/media/"+id);prepared.add(photo);
        }
        photos.saveAll(prepared);
    }
    public boolean published(String id) { return photos.existsByBaiduFileIdAndDeletedFalse(id); }
    public record Media(InputStream input,String contentType) {}
    public Media media(String id) { return readMedia(id, false); }
    public Media thumbnail(String id) { return readMedia(id, true); }
    private Media readMedia(String id, boolean thumbnail) {
        JsonNode file=metadata(id);
        String expected=mime(file.path("path").asText());
        if(thumbnail || isHeif(expected)) {
            for(String size:thumbnail ? List.of("url1","url2","url3") : List.of("url3","url2","url1")) {
                String preview=file.path("thumbs").path(size).asText();
                if(!preview.isBlank()) {
                    try {return openMedia(preview,expected);} catch(ResponseStatusException unavailable) { /* 回退原图，由浏览器转换。 */ }
                }
            }
        }
        return openMedia(file.path("dlink").asText(),expected);
    }
    protected Media openMedia(String link,String expected) {
        try {
            URI uri=URI.create(link+(link.contains("?")?"&":"?")+"access_token="+URLEncoder.encode(accessToken(),StandardCharsets.UTF_8));
            for(int redirects=0;redirects<5;redirects++) {
                String host=uri.getHost();
                if(!"https".equals(uri.getScheme()) || host==null || !(host.endsWith(".baidu.com")||host.endsWith(".baidupcs.com")) || uri.getUserInfo()!=null || (uri.getPort()!=-1 && uri.getPort()!=443)) throw fail(502,"百度返回了不支持的图片地址");
                var connection=(java.net.HttpURLConnection)uri.toURL().openConnection();
                connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(10000);connection.setReadTimeout(30000);
                connection.setRequestProperty("User-Agent","pan.baidu.com");
                int status=connection.getResponseCode();
                if(status>=300 && status<400) {String location=connection.getHeaderField("Location");connection.disconnect();uri=uri.resolve(Objects.requireNonNull(location));continue;}
                if(status!=200) {connection.disconnect();throw fail(502,"照片读取失败，请重新授权或稍后重试");}
                String type=Objects.requireNonNullElse(connection.getContentType(),"").split(";")[0];
                java.io.InputStream input=connection.getInputStream();
                if(isHeif(expected) && Set.of("image/heic","image/heif","application/octet-stream").contains(type)) {
                    byte[] prefix=input.readNBytes(64);
                    try {type=imageType(prefix);} catch(ResponseStatusException invalid) {input.close();connection.disconnect();throw invalid;}
                    if(!isHeif(type)) {input.close();connection.disconnect();throw fail(502,"网盘返回的原图格式不匹配");}
                    input=new java.io.SequenceInputStream(new java.io.ByteArrayInputStream(prefix),input);
                }
                if(!Set.of("image/jpeg","image/png","image/webp","image/gif","image/heic","image/heif").contains(type)) {input.close();connection.disconnect();throw fail(502,"网盘返回的内容不是支持的图片");}
                return new Media(new java.io.FilterInputStream(input) {
                    @Override public void close() throws java.io.IOException {try {super.close();} finally {connection.disconnect();}}
                },type);
            }
        } catch(ResponseStatusException ex) {throw ex;} catch(Exception ex) {if(ex instanceof InterruptedException) Thread.currentThread().interrupt();}
        throw fail(502,"网盘图片加载失败");
    }
    /** 小图片在有界内存中分片，不使用 MultipartFile 或本地临时文件。 */
    public record Uploaded(String id, String path, String contentType) {}
    public Uploaded uploadImage(byte[] data, String originalName) {
        String type=imageType(data);
        if(data.length>20*1024*1024) throw fail(413,"单张照片最多 20MB");
        String extension=switch(type) {case "image/jpeg" -> ".jpg";case "image/png" -> ".png";case "image/webp" -> ".webp";case "image/heic" -> ".heic";case "image/heif" -> ".heif";default -> ".gif";};
        String path=checkedPath(root()+"/"+UUID.randomUUID()+extension);
        final int blockSize=4*1024*1024;
        var hashes=new ArrayList<String>();
        try {
            for(int offset=0;offset<data.length;offset+=blockSize) {
                var digest=java.security.MessageDigest.getInstance("MD5");digest.update(data,offset,Math.min(blockSize,data.length-offset));
                hashes.add(HexFormat.of().formatHex(digest.digest()));
            }
            String blocks=json.writeValueAsString(hashes);
            var form=new HashMap<String,String>(Map.of("path",path,"size",String.valueOf(data.length),"isdir","0","autoinit","1","block_list",blocks,"rtype","0"));
            JsonNode pre=postForm(Map.of("method","precreate"),form);
            JsonNode created;
            if(pre.path("return_type").asInt()==2) {
                // 秒传完成时按随机生成的完整路径找回 ID，不能误绑定同名其他照片。
                created=null;
                JsonNode listing=api("file",Map.of("method","search","dir",root(),"key",Path.of(path).getFileName().toString(),"recursion","0"));
                for(JsonNode item:listing.path("list")) if(path.equals(item.path("path").asText())) created=item;
                if(created==null) throw fail(502,"照片已上传，但未能读取文件标识，请在网盘管理面板检查");
            } else {
                String uploadId=pre.path("uploadid").asText();
                if(uploadId.isBlank() || !pre.path("block_list").isArray()) throw fail(502,"网盘未返回有效上传会话");
                for(JsonNode index:pre.path("block_list")) {
                    int part=index.asInt(-1);
                    if(part<0 || part>=hashes.size()) throw fail(502,"网盘返回了无效分片序号");
                    String md5=uploadPart(path,uploadId,part,data,part*blockSize,Math.min(blockSize,data.length-part*blockSize));
                    if(!hashes.get(part).equalsIgnoreCase(md5)) throw fail(502,"照片分片校验失败，请重新上传");
                }
                form.remove("autoinit");form.put("uploadid",uploadId);
                created=postForm(Map.of("method","create"),form);
            }
            String id=created.path("fs_id").asText();
            if(!id.matches("[0-9]{1,20}")) throw fail(502,"网盘未返回照片标识，请在管理面板检查");
            return new Uploaded(id,path,type);
        } catch(ResponseStatusException ex) {throw ex;} catch(Exception ex) {throw fail(502,"网盘上传失败，请稍后重试");}
    }
    protected JsonNode postForm(Map<String,String> params,Map<String,String> form) {
        var queryParams=new HashMap<>(params);queryParams.put("access_token",accessToken());
        return sendUpload(HttpRequest.newBuilder(URI.create("https://pan.baidu.com/rest/2.0/xpan/file?"+query(queryParams)))
            .header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(query(form))).build());
    }
    protected String uploadPart(String path,String uploadId,int part,byte[] bytes,int offset,int length) {
        String boundary="evan"+UUID.randomUUID().toString().replace("-","");
        String head="--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"part\"\r\nContent-Type: application/octet-stream\r\n\r\n";
        var body=HttpRequest.BodyPublishers.concat(HttpRequest.BodyPublishers.ofString(head),HttpRequest.BodyPublishers.ofByteArray(bytes,offset,length),HttpRequest.BodyPublishers.ofString("\r\n--"+boundary+"--\r\n"));
        String url="https://d.pcs.baidu.com/rest/2.0/pcs/superfile2?"+query(Map.of("method","upload","type","tmpfile","path",path,"uploadid",uploadId,"partseq",String.valueOf(part),"access_token",accessToken()));
        return sendUpload(HttpRequest.newBuilder(URI.create(url)).header("Content-Type","multipart/form-data; boundary="+boundary).POST(body).build()).path("md5").asText();
    }
    private JsonNode sendUpload(HttpRequest request) {
        try {
            var bounded=HttpRequest.newBuilder(request.uri()).timeout(Duration.ofSeconds(90)).header("User-Agent","pan.baidu.com");
            request.headers().map().forEach((k,values)->values.forEach(v->bounded.header(k,v)));
            bounded.method(request.method(),request.bodyPublisher().orElse(HttpRequest.BodyPublishers.noBody()));
            var response=http.send(bounded.build(),HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()!=200) throw fail(502,"百度写入接口暂不可用");
            JsonNode result=json.readTree(response.body());
            if(result.has("error") || result.path("errno").asInt()!=0 || result.path("error_code").asInt()!=0) throw fail(502,"网盘写入失败，请检查目录、容量和应用上传/删除权限");
            return result;
        } catch(ResponseStatusException ex) {throw ex;} catch(Exception ex) {if(ex instanceof InterruptedException) Thread.currentThread().interrupt();throw fail(502,"网盘写入连接失败，请稍后重试");}
    }
    public void deleteOriginal(String id) {
        JsonNode file;
        try {file=metadata(id);} catch(ResponseStatusException ex) {if(ex.getStatusCode().value()==404) return;throw ex;}
        try {
            String path=checkedPath(file.path("path").asText());
            JsonNode result=postForm(Map.of("method","filemanager","opera","delete"),Map.of("async","0","filelist",json.writeValueAsString(List.of(path))));
            JsonNode info=result.path("info");
            if(!info.isArray() || info.size()!=1 || info.get(0).path("errno").asInt(-1)!=0) throw fail(502,"网盘未确认原图删除，相册记录已保留，请稍后重试");
        } catch(ResponseStatusException ex) {throw ex;} catch(Exception ex) {throw fail(502,"网盘删除失败，相册记录已保留");}
    }
    static String imageType(byte[] bytes) {
        if(bytes.length>=3 && (bytes[0]&255)==255 && (bytes[1]&255)==216 && (bytes[2]&255)==255) return "image/jpeg";
        if(bytes.length>=8 && Arrays.equals(Arrays.copyOf(bytes,8),new byte[]{(byte)137,80,78,71,13,10,26,10})) return "image/png";
        if(bytes.length>=6 && (new String(bytes,0,6,StandardCharsets.US_ASCII).equals("GIF87a") || new String(bytes,0,6,StandardCharsets.US_ASCII).equals("GIF89a"))) return "image/gif";
        if(bytes.length>=12 && new String(bytes,0,4,StandardCharsets.US_ASCII).equals("RIFF") && new String(bytes,8,4,StandardCharsets.US_ASCII).equals("WEBP")) return "image/webp";
        if(bytes.length>=16 && new String(bytes,4,4,StandardCharsets.US_ASCII).equals("ftyp")) {
            long boxSize=Integer.toUnsignedLong(java.nio.ByteBuffer.wrap(bytes,0,4).getInt());
            int end=(int)Math.min(Math.min(boxSize,bytes.length),256);
            boolean hevc=false,heif=false,avif=false;
            for(int offset=8;offset+4<=end;offset+=4) {
                if(offset==12) continue; // 次版本号不是品牌。
                String brand=new String(bytes,offset,4,StandardCharsets.US_ASCII);
                hevc|=Set.of("heic","heix","hevc","hevx","heim","heis","hevm","hevs").contains(brand);
                heif|=Set.of("mif1","msf1").contains(brand);
                avif|=Set.of("avif","avis").contains(brand);
            }
            if(!avif && hevc) return "image/heic";
            if(!avif && heif) return "image/heif";
        }
        throw fail(400,"只支持 JPG、PNG、WebP、GIF、HEIC、HEIF 照片，请勿上传视频或其他文件");
    }

    static boolean isHeif(String type) {return "image/heic".equals(type)||"image/heif".equals(type);}
    static String mime(String name) {
        String n=name.toLowerCase(Locale.ROOT);
        if(n.endsWith(".jpg")||n.endsWith(".jpeg")) return "image/jpeg";
        if(n.endsWith(".png")) return "image/png";
        if(n.endsWith(".webp")) return "image/webp";
        if(n.endsWith(".gif")) return "image/gif";
        if(n.endsWith(".heic")) return "image/heic";
        if(n.endsWith(".heif")) return "image/heif";
        return null;
    }
    static ResponseStatusException fail(int code,String message) {return new ResponseStatusException(HttpStatus.valueOf(code),message);}
}
