package com.evanlink.netdisk;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.server.ResponseStatusException;
import com.evanlink.service.AlbumPhotoService;
import com.evanlink.repository.AlbumPhotoRepository;
import com.evanlink.model.AlbumPhoto;
import java.util.*;
import java.lang.reflect.Proxy;

class NetdiskWriteTest {
    private final ObjectMapper json=new ObjectMapper();
    @Test void multiBlockUploadUsesCorrectOffsetsAndValidatesChecksums() throws Exception {
        byte[] data=new byte[4*1024*1024+10];data[0]=(byte)255;data[1]=(byte)216;data[2]=(byte)255;
        var calls=new ArrayList<String>();
        var disk=new BaiduNetdiskService(new MockEnvironment(),null,null,json) {
            @Override protected JsonNode postForm(Map<String,String> params,Map<String,String> form) {
                calls.add(params.get("method"));assertTrue(form.get("path").startsWith("/evan-blog-album/"));assertEquals("0",form.get("rtype"));
                try {return json.readTree(params.get("method").equals("precreate")?"{\"return_type\":1,\"uploadid\":\"u\",\"block_list\":[0,1]}":"{\"fs_id\":123}");}catch(Exception ex){throw new RuntimeException(ex);}
            }
            @Override protected String uploadPart(String path,String uploadId,int part,byte[] bytes,int offset,int length) {
                calls.add("part"+part);assertEquals(part*4*1024*1024,offset);assertEquals(part==0?4*1024*1024:10,length);
                try {var md5=java.security.MessageDigest.getInstance("MD5");md5.update(bytes,offset,length);return HexFormat.of().formatHex(md5.digest());}catch(Exception ex){throw new RuntimeException(ex);}
            }
        };
        assertEquals("123",disk.uploadImage(data,"photo.jpg").id());
        assertEquals(List.of("precreate","part0","part1","create"),calls);
    }
    @Test void disguisedHtmlIsRejectedBeforeAnyRemoteWrite() {
        var disk=new BaiduNetdiskService(new MockEnvironment(),null,null,json);
        assertThrows(ResponseStatusException.class,()->disk.uploadImage("<html>not an image</html>".getBytes(),"photo.jpg"));
    }
    @Test void failedRemoteDeletePreservesDatabaseRecord() {
        AlbumPhoto photo=new AlbumPhoto();photo.setBaiduFileId("123");
        var operations=new ArrayList<String>();
        var repo=(AlbumPhotoRepository)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{AlbumPhotoRepository.class},(p,m,a)->{
            operations.add(m.getName());if(m.getName().equals("findByIdAndDeletedFalse"))return Optional.of(photo);return photo;
        });
        var disk=new BaiduNetdiskService(new MockEnvironment(),null,null,json) {
            @Override public void deleteOriginal(String id) {throw BaiduNetdiskService.fail(502,"remote failed");}
        };
        assertThrows(ResponseStatusException.class,()->new AlbumPhotoService(repo,disk).delete(1L));
        assertFalse(photo.getDeleted());assertFalse(operations.contains("saveAndFlush"));
    }
    @Test void deleteIsConfirmedRemotelyBeforeLocalRemoval() {
        AlbumPhoto photo=new AlbumPhoto();photo.setBaiduFileId("123");var events=new ArrayList<String>();
        var repo=(AlbumPhotoRepository)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{AlbumPhotoRepository.class},(p,m,a)->{
            if(m.getName().equals("findByIdAndDeletedFalse"))return Optional.of(photo);
            if(m.getName().equals("saveAndFlush")){events.add("database");assertTrue(photo.getDeleted());}return photo;
        });
        var disk=new BaiduNetdiskService(new MockEnvironment(),null,null,json) {@Override public void deleteOriginal(String id){events.add("baidu");}};
        new AlbumPhotoService(repo,disk).delete(1L);assertEquals(List.of("baidu","database"),events);
    }
    @Test void failedDatabaseWriteCompensatesUploadedOriginal() {
        var removed=new ArrayList<String>();
        var repo=(AlbumPhotoRepository)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{AlbumPhotoRepository.class},(p,m,a)->{throw new IllegalStateException("database unavailable");});
        var disk=new BaiduNetdiskService(new MockEnvironment(),null,null,json) {
            @Override public Uploaded uploadImage(byte[] data,String name){return new Uploaded("123","/evan-blog-album/a.jpg","image/jpeg");}
            @Override public void deleteOriginal(String id){removed.add(id);}
        };
        assertThrows(ResponseStatusException.class,()->new AlbumPhotoService(repo,disk).upload(new byte[]{1},"a.jpg",null,null));
        assertEquals(List.of("123"),removed);
    }
    @Test void itemLevelDeleteErrorCannotBeTreatedAsSuccess() {
        var disk=new BaiduNetdiskService(new MockEnvironment(),null,null,json) {
            @Override protected JsonNode metadata(String id){return json.createObjectNode().put("path","/evan-blog-album/a.jpg");}
            @Override protected JsonNode postForm(Map<String,String> params,Map<String,String> form){
                assertEquals("delete",params.get("opera"));assertEquals("0",form.get("async"));
                var result=json.createObjectNode();result.putArray("info").addObject().put("errno",-1);return result;
            }
        };
        assertThrows(ResponseStatusException.class,()->disk.deleteOriginal("123"));
    }
}
