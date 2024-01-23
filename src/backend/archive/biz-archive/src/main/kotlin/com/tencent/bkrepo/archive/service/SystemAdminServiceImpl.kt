package com.tencent.bkrepo.archive.service

import com.tencent.bkrepo.archive.job.Cancellable
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import org.springframework.stereotype.Service

@Service
class SystemAdminServiceImpl : SystemAdminService {
    override fun stop(jobName: String) {
        val cancellable = SpringContextUtils.getBean(Cancellable::class.java, jobName)
        cancellable.cancel()
    }
}
