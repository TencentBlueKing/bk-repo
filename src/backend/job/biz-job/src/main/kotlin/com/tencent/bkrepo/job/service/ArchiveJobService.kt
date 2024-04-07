package com.tencent.bkrepo.job.service

import com.tencent.bkrepo.archive.constant.ArchiveStorageClass

interface ArchiveJobService {
    fun archive(projectId: String, key: String, days: Int, storageClass: ArchiveStorageClass)
}
