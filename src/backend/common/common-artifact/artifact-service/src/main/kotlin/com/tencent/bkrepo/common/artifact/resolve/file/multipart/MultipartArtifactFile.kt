package com.tencent.bkrepo.common.artifact.resolve.file.multipart

import com.tencent.bkrepo.common.artifact.resolve.file.stream.OctetStreamArtifactFile
import com.tencent.bkrepo.common.storage.core.StorageProperties
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.common.storage.monitor.StorageHealthMonitor
import org.springframework.web.multipart.MultipartFile

class MultipartArtifactFile(
    private val multipartFile: MultipartFile,
    monitor: StorageHealthMonitor,
    storageProperties: StorageProperties,
    storageCredentials: StorageCredentials
) : OctetStreamArtifactFile(multipartFile.inputStream, monitor, storageProperties, storageCredentials) {
    fun getOriginalFilename() = multipartFile.originalFilename.orEmpty()
}
