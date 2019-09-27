package com.tencent.bkrepo.common.storage.core

/**
 * 仓库类别枚举类
 *
 * @author: carrypan
 * @date: 2019-09-10
 */
enum class StorageTypeEnum {
    LOCAL, // 本地文件系统存储
    INNER_COS, // 内部cos
    COS, // 腾讯云cos
    S3, // 标准S3
    CEPH_FS, // CephFS
}
