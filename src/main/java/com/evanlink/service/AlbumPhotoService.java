package com.evanlink.service;

import com.evanlink.dto.AlbumPhotoResponse;
import com.evanlink.model.AlbumPhoto;
import com.evanlink.netdisk.BaiduNetdiskService;
import com.evanlink.repository.AlbumPhotoRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.List;
import java.util.NoSuchElementException;

/** 相册仅写入网盘；数据库只保存元数据。旧本地记录保留，待迁移。 */
@Service
public class AlbumPhotoService {
    private final AlbumPhotoRepository repository;
    private final BaiduNetdiskService disk;
    public AlbumPhotoService(AlbumPhotoRepository repository,BaiduNetdiskService disk) {this.repository=repository;this.disk=disk;}
    /** 数据库中的未删除记录即已发布相册；网盘未导入文件不在此列表中。 */
    public List<AlbumPhotoResponse> list() {
        return repository.findByDeletedFalseOrderBySortOrderAscCreatedAtDesc().stream()
            .filter(AlbumPhoto::isPublished)
            .map(AlbumPhotoResponse::from).toList();
    }
    public AlbumPhotoResponse upload(byte[] data,String name,String title,String description) {
        if(name==null || name.isBlank() || name.length()>255 || (title!=null && title.length()>200) || (description!=null && description.length()>500)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"照片名称、标题或描述长度无效");
        var uploaded=disk.uploadImage(data,name);
        var photo=new AlbumPhoto();photo.setBaiduFileId(uploaded.id());photo.setUrl("/api/album/baidu/media/"+uploaded.id());photo.setOriginalName(name);photo.setContentType(uploaded.contentType());photo.setFileSize((long)data.length);photo.setTitle(title);photo.setDescription(description);
        try {return AlbumPhotoResponse.from(repository.saveAndFlush(photo));}
        catch(RuntimeException failure) {
            // 避免数据库写入失败时留下无引用的原图；清理失败明确提示用户核对网盘。
            try {disk.deleteOriginal(uploaded.id());}
            catch(RuntimeException cleanup) {throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"照片已存到网盘，但相册保存和自动清理失败，请在网盘面板核对后重新导入");}
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"相册保存失败，已撤销本次网盘上传，请重试");
        }
    }
    public synchronized void delete(Long id) {
        var photo=repository.findByIdAndDeletedFalse(id).orElseThrow(NoSuchElementException::new);
        if(photo.getBaiduFileId()==null) throw new ResponseStatusException(HttpStatus.CONFLICT,"这是历史本地照片，请先迁移到网盘；本次未删除文件");
        // 远端失败必须保留记录，不能在网盘原图仍存在时提示删除成功。
        disk.deleteOriginal(photo.getBaiduFileId());
        photo.setDeleted(true);repository.saveAndFlush(photo);
    }
}
