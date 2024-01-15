package com.tencent.bkrepo.archive.job.compress

import com.tencent.bkrepo.archive.job.BaseJobSubscriber
import com.tencent.bkrepo.archive.model.TCompressFile
import com.tencent.bkrepo.archive.service.CompressService

class CompressSubscriber(
    private val compressService: CompressService,
) : BaseJobSubscriber<TCompressFile>() {

    override fun doOnNext(value: TCompressFile) {
        compressService.compress(value)
    }

    override fun getSize(value: TCompressFile): Long {
        return value.uncompressedSize
    }
}
