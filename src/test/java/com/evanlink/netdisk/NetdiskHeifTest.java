package com.evanlink.netdisk;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.server.ResponseStatusException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.io.ByteArrayInputStream;
import static org.junit.jupiter.api.Assertions.*;

class NetdiskHeifTest {
    private final ObjectMapper json=new ObjectMapper();
    private byte[] header(String major,String compatible) {
        return ByteBuffer.allocate(24).putInt(24).put("ftyp".getBytes(StandardCharsets.US_ASCII)).put(major.getBytes(StandardCharsets.US_ASCII)).putInt(0).put(compatible.getBytes(StandardCharsets.US_ASCII)).put(compatible.getBytes(StandardCharsets.US_ASCII)).array();
    }
    @Test void detectsHeicHeifExtensionsAndContainerBrands() {
        assertEquals("image/heic",BaiduNetdiskService.mime("IMG_1234.HEIC"));
        assertEquals("image/heif",BaiduNetdiskService.mime("IMG_1234.HeIf"));
        assertEquals("image/heic",BaiduNetdiskService.imageType(header("heic","heic")));
        assertEquals("image/heic",BaiduNetdiskService.imageType(header("mif1","heix")));
        assertEquals("image/heif",BaiduNetdiskService.imageType(header("mif1","mif1")));
        assertThrows(ResponseStatusException.class,()->BaiduNetdiskService.imageType(header("avif","avif")));
        assertThrows(ResponseStatusException.class,()->BaiduNetdiskService.imageType(header("mp42","mp42")));
    }
    @Test void directoryListingDoesNotFilterOutHeicOrHeif() throws Exception {
        var response=json.readTree("{\"list\":[{\"fs_id\":1,\"server_filename\":\"IMG.HEIC\",\"path\":\"/evan-blog-album/IMG.HEIC\"},{\"fs_id\":2,\"server_filename\":\"IMG2.heif\",\"path\":\"/evan-blog-album/IMG2.heif\"},{\"fs_id\":3,\"server_filename\":\"note.txt\"}]}");
        var service=new BaiduNetdiskService(new MockEnvironment(),null,null,json) {@Override protected JsonNode api(String endpoint,Map<String,String> params){return response;}};
        assertEquals(2,((List<?>)service.files("/evan-blog-album",1).get("items")).size());
    }
    @Test void metadataRequestsPreviewAndAcceptsHeic() throws Exception {
        var response=json.readTree("{\"list\":[{\"path\":\"/evan-blog-album/IMG.HEIC\",\"size\":1024}]}");
        var service=new BaiduNetdiskService(new MockEnvironment(),null,null,json) {
            @Override protected JsonNode api(String endpoint,Map<String,String> params){assertEquals("1",params.get("thumb"));return response;}
        };
        assertEquals("/evan-blog-album/IMG.HEIC",service.metadata("123").path("path").asText());
    }
    @Test void prefersPreviewButFallsBackToOriginalWhenUnavailable() throws Exception {
        var file=json.readTree("{\"path\":\"/evan-blog-album/IMG.HEIC\",\"dlink\":\"https://d.pcs.baidu.com/original\",\"thumbs\":{\"url3\":\"https://thumbnail.baidupcs.com/preview\"}}");
        var calls=new ArrayList<String>();
        var service=new BaiduNetdiskService(new MockEnvironment(),null,null,json) {
            @Override protected JsonNode metadata(String id){return file;}
            @Override protected Media openMedia(String link,String expected){
                calls.add(link);assertEquals("image/heic",expected);
                if(link.endsWith("preview"))throw BaiduNetdiskService.fail(502,"preview unavailable");
                return new Media(new ByteArrayInputStream(new byte[0]),expected);
            }
        };
        assertEquals("image/heic",service.media("123").contentType());assertEquals(2,calls.size());assertTrue(calls.get(0).endsWith("preview"));assertTrue(calls.get(1).endsWith("original"));
    }
    @Test void thumbnailUsesSmallPreviewForOrdinaryJpeg() throws Exception {
        var file=json.readTree("{\"path\":\"/evan-blog-album/IMG.jpg\",\"dlink\":\"https://d.pcs.baidu.com/original\",\"thumbs\":{\"url1\":\"https://thumbnail.baidupcs.com/small\",\"url3\":\"https://thumbnail.baidupcs.com/large\"}}");
        var calls=new ArrayList<String>();
        var service=new BaiduNetdiskService(new MockEnvironment(),null,null,json) {
            @Override protected JsonNode metadata(String id){return file;}
            @Override protected Media openMedia(String link,String expected){calls.add(link);return new Media(new ByteArrayInputStream(new byte[0]),"image/jpeg");}
        };
        service.thumbnail("123"); assertTrue(calls.get(0).endsWith("small"));
        calls.clear();service.media("123");assertTrue(calls.get(0).endsWith("original"));
    }

}
