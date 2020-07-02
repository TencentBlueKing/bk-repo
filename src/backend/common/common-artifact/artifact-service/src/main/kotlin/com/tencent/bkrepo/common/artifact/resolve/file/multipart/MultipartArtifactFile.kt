package com.tencent.bkrepo.common.artifact.resolve.file.multipart

import com.tencent.bkrepo.common.artifact.resolve.file.stream.OctetStreamArtifactFile
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.common.storage.monitor.StorageHealthMonitor
import org.springframework.web.multipart.MultipartFile

class MultipartArtifactFile(
    private val multipartFile: MultipartFile,
    monitor: StorageHealthMonitor,
    storageCredentials: StorageCredentials?
) : OctetStreamArtifactFile(multipartFile.inputStream, monitor, storageCredentials) {
    fun getOriginalFilename() = multipartFile.originalFilename.orEmpty()
}
