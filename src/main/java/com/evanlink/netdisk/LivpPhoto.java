package com.evanlink.netdisk;

import java.io.*;
import java.util.zip.ZipInputStream;

/** 从 LIVP 容器流式读出静态照片，不解压到磁盘、不解码视频。 */
final class LivpPhoto {
    private static final int LIMIT=20*1024*1024;
    static BaiduNetdiskService.Media extract(InputStream source) throws IOException {
        var zip=new ZipInputStream(source);
        long skipped=0;
        byte[] buffer=new byte[8192];
        try {
        for(int entries=0;entries<32;entries++) {
            var entry=zip.getNextEntry();
            if(entry==null) break;
            String name=entry.getName();
            String mime=BaiduNetdiskService.mime(name);
            if(!entry.isDirectory() && !name.startsWith("__MACOSX/") && mime!=null && mime.startsWith("image/")) {
                if(entry.getSize()>LIMIT) throw BaiduNetdiskService.fail(413,"LIVP 内照片超过 20MB");
                byte[] prefix=zip.readNBytes(64);
                String actual=BaiduNetdiskService.imageType(prefix);
                InputStream image=new SequenceInputStream(new ByteArrayInputStream(prefix),zip);
                return new BaiduNetdiskService.Media(new FilterInputStream(image) {
                    private long read;
                    private void count(int n) throws IOException {if(n>0 && (read+=n)>LIMIT) throw new IOException("LIVP photo exceeds limit");}
                    @Override public int read() throws IOException {int n=in.read();count(n<0?0:1);return n;}
                    @Override public int read(byte[] b,int off,int len) throws IOException {int n=in.read(b,off,len);count(n);return n;}
                },actual);
            }
            // 显式限制被跳过的视频/其他条目的解压量，避免 ZIP 炸弹。
            int n;
            while((n=zip.read(buffer))!=-1) if((skipped+=n)>LIMIT) throw BaiduNetdiskService.fail(413,"LIVP 包内容过大，请导出静态照片后上传");
        }
        throw BaiduNetdiskService.fail(422,"LIVP 中没有可识别的照片");
        } catch(IOException | RuntimeException failure) {
            try {zip.close();} catch(IOException closeFailure) {failure.addSuppressed(closeFailure);}
            throw failure;
        }
    }
}
