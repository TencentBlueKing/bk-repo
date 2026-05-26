package com.tencent.bkrepo.job.batch.utils

import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials

object DriveUtils {
    fun notAllowDriveRepository(credentials: StorageCredentials): Boolean {
        val allowRepoTypes = credentials.allowRepoTypes
        return allowRepoTypes?.isNotEmpty() == true && !allowRepoTypes.contains(RepositoryType.DRIVE.name) ||
                credentials.notAllowRepoTypes?.contains(RepositoryType.DRIVE.name) == true
    }
}