package com.evanlink.netdisk;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import java.lang.reflect.Proxy;
import com.evanlink.model.AlbumPhoto;
import com.evanlink.repository.AlbumPhotoRepository;
import com.evanlink.service.AlbumPhotoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.server.ResponseStatusException;

class LivpAndPublicationTest {
    byte[] zip(Map<String,byte[]> entries) throws IOException {
        var bytes=new ByteArrayOutputStream();
        try(var zip=new ZipOutputStream(bytes)) {for(var entry:entries.entrySet()){zip.putNextEntry(new ZipEntry(entry.getKey()));zip.write(entry.getValue());zip.closeEntry();}}
        return bytes.toByteArray();
    }
    @Test void extractsStillImageWithoutVideoOrDisk() throws Exception {
        byte[] jpg={(byte)255,(byte)216,(byte)255,1,2,3};
        var entries=new LinkedHashMap<String,byte[]>();entries.put("photo.MOV",new byte[]{1,2,3});entries.put("photo.JPG",jpg);
        var media=LivpPhoto.extract(new ByteArrayInputStream(zip(entries)));
        assertEquals("image/jpeg",media.contentType());try(var stream=media.input()){assertArrayEquals(jpg,stream.readAllBytes());}
        assertEquals("application/x-livp",BaiduNetdiskService.mime("PHOTO.LIVP"));
    }
    @Test void rejectsArchiveWithoutPhotoAndOversizedSkippedEntry() throws Exception {
        byte[] missing=zip(Map.of("photo.mov",new byte[]{1}));
        assertThrows(ResponseStatusException.class,()->LivpPhoto.extract(new ByteArrayInputStream(missing)));
        byte[] bomb=zip(Map.of("photo.mov",new byte[20*1024*1024+1]));
        assertThrows(ResponseStatusException.class,()->LivpPhoto.extract(new ByteArrayInputStream(bomb)));
    }
    @Test void unpublishingHidesListAndPublicAccessButRetainsRecordAndOriginal() {
        var photo=new AlbumPhoto();photo.setId(1L);photo.setBaiduFileId("123");photo.setUrl("/api/album/baidu/media/123");
        var calls=new ArrayList<String>();
        var repo=(AlbumPhotoRepository)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{AlbumPhotoRepository.class},(p,m,a)->{
            calls.add(m.getName());return switch(m.getName()) {
                case "findByDeletedFalseAndBaiduFileIdIn", "findByDeletedFalseOrderBySortOrderAscCreatedAtDesc" -> List.of(photo);
                case "existsByBaiduFileIdAndDeletedFalseAndPublishedTrue" -> photo.isPublished();
                case "saveAll" -> a[0];
                case "findByBaiduFileIdAndDeletedFalse" -> Optional.of(photo);
                default -> throw new AssertionError(m.getName());
            };
        });
        var disk=new BaiduNetdiskService(new MockEnvironment(),null,repo,new ObjectMapper()) {
            @Override protected com.fasterxml.jackson.databind.JsonNode metadata(String id){return new ObjectMapper().createObjectNode().put("path","/evan-blog-album/photo.jpg");}
            @Override public void deleteOriginal(String id){org.junit.jupiter.api.Assertions.fail("Must not delete remote file when hiding");}
        };
        assertTrue(disk.published("123"));disk.unpublish(List.of("123"));
        assertFalse(disk.published("123"));assertTrue(new AlbumPhotoService(repo,disk).list().isEmpty());assertFalse(photo.getDeleted());
        assertFalse(calls.stream().anyMatch(name->name.startsWith("delete")));
        disk.publish(List.of("123"));assertTrue(disk.published("123"));assertEquals(1,new AlbumPhotoService(repo,disk).list().size());
    }
}
