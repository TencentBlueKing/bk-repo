package com.tencent.bkrepo.common.artifact.resolve.file

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import org.apache.commons.fileupload.disk.DiskFileItem
import java.io.File
import java.io.InputStream

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

    override fun getSize(): Long = diskFileItem.size

    override fun delete() = diskFileItem.delete()

    override fun isInMemory() = diskFileItem.isInMemory

    /**
     * 该方法在DiskFileItem中为protected，采用反射调用
     */
    override fun getFile(): File {
        val method = diskFileItem.javaClass.getDeclaredMethod(ArtifactFile::getFile.name)
        method.isAccessible = true
        return method.invoke(diskFileItem) as File
    }

    fun getOriginalFilename(): String {
        return diskFileItem.name
    }
}
