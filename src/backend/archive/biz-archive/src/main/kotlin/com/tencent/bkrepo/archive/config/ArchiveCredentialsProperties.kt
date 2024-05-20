package com.tencent.bkrepo.archive.config

import com.tencent.bkrepo.archive.constant.ArchiveRestoreTier
import com.tencent.bkrepo.archive.constant.ArchiveStorageClass
import com.tencent.bkrepo.common.storage.credentials.InnerCosCredentials
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials

data class ArchiveCredentialsProperties(
    /**
     * 归档实例
     * */
    var cos: StorageCredentials = InnerCosCredentials(),
    /**
     * 归档类型
     * */
    var storageClass: ArchiveStorageClass = ArchiveStorageClass.DEEP_ARCHIVE,
    /**
     * 恢复出的临时副本的有效时长，单位为“天”
     * */
    var days: Int = 1,
    /**
     * 恢复模式
     * */
    var tier: ArchiveRestoreTier = ArchiveRestoreTier.Standard,

    /**
     * 恢复数量限制
     * */
    var restoreLimit: Int = 1000,
)
