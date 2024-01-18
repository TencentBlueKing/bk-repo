package com.tencent.bkrepo.archive.service

import com.tencent.bkrepo.archive.model.TCompressFile
import com.tencent.bkrepo.archive.pojo.CompressFile
import com.tencent.bkrepo.archive.request.CompleteCompressRequest
import com.tencent.bkrepo.archive.request.CompressFileRequest
import com.tencent.bkrepo.archive.request.DeleteCompressRequest
import com.tencent.bkrepo.archive.request.UncompressFileRequest

/**
 * 压缩服务
 * */
interface CompressService {
    /**
     * 压缩文件
     * @return 1表示压缩成功，0表示未压缩
     * */
    fun compress(request: CompressFileRequest)

    /**
     * 解压文件
     * */

    fun uncompress(request: UncompressFileRequest)

    /**
     * 删除压缩文件
     * */

    fun delete(request: DeleteCompressRequest)

    /**
     * 完成压缩
     * */
    fun complete(request: CompleteCompressRequest)

    /**
     * 获取压缩信息
     * */
    fun getCompressInfo(
        sha256: String,
        storageCredentialsKey: String?,
    ): CompressFile?

    /**
     * 压缩文件
     * */
    fun compress(file: TCompressFile)

    /**
     * 解压文件
     * */
    fun uncompress(file: TCompressFile)
}
