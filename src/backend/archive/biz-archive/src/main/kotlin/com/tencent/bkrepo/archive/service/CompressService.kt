package com.tencent.bkrepo.archive.service

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
}
