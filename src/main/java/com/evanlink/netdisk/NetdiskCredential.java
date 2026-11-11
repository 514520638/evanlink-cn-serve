package com.evanlink.netdisk;

import jakarta.persistence.*;

/** 单个博客主人的网盘授权，只保存加密后的令牌。 */
@Entity
@Table(name = "baidu_netdisk_credential")
public class NetdiskCredential {
    @Id public Long id = 1L;
    @Column(nullable = false, columnDefinition = "TEXT") public String encryptedToken;
}
