package com.evanlink.album;
import jakarta.persistence.*;
@Entity
@Table(name="album_like",uniqueConstraints=@UniqueConstraint(columnNames={"photo_id","visitor_hash"}))
public class AlbumLike {
    @Id public String id;
    @Column(name="photo_id",nullable=false) public Long photoId;
    @Column(name="visitor_hash",nullable=false,length=64) public String visitorHash;
}
