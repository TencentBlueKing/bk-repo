package com.tencent.bkrepo.common.artifact.resolve.file

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import org.apache.commons.fileupload.disk.DiskFileItem

/**
 *
 * 适配器模式，将apache commons fileupload DiskFileItem适配ArtifactFile接口
 * 该类用于通过multipart方式上传文件时，转换为ArtifactFile
 *
 * @author: carrypan
 * @date: 2019/10/30
 */
class MultipartArtifactFile(private val diskFileItem: DiskFileItem) : ArtifactFile {

    override fun getInputStream(): InputStream = diskFileItem.inputStream

    override fun getOutputStream(): OutputStream = diskFileItem.outputStream

    override fun getSize(): Long = diskFileItem.size

    override fun delete() = diskFileItem.delete()

    /**
     * 该方法在DiskFileItem中为protected，采用反射调用
     */
    override fun getTempFile(): File {
        return diskFileItem.javaClass.getMethod("getTempFile").invoke(null) as File
    }
}
