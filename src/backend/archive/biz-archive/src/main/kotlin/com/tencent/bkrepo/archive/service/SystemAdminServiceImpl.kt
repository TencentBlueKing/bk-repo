package com.tencent.bkrepo.archive.service

import com.tencent.bkrepo.archive.core.FileCoreProcessor
import org.springframework.stereotype.Service

@Service
class SystemAdminServiceImpl(private val processor: FileCoreProcessor) : SystemAdminService {
    override fun stop() {
        processor.shutdown()
    }
}
