package com.tencent.bkrepo.common.artifact.resolve.file.multipart

import com.tencent.bkrepo.common.artifact.resolve.file.UploadConfigElement
import com.tencent.bkrepo.common.artifact.resolve.file.stream.OctetStreamArtifactFile
import com.tencent.bkrepo.common.storage.monitor.StorageHealthMonitor
import org.springframework.web.multipart.MultipartFile

class MultipartArtifactFile(
    private val multipartFile: MultipartFile,
    monitor: StorageHealthMonitor,
    config: UploadConfigElement
) : OctetStreamArtifactFile(multipartFile.inputStream, monitor, config) {
    fun getOriginalFilename() = multipartFile.originalFilename.orEmpty()
}
