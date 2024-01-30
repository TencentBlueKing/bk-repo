package com.tencent.bkrepo.archive.config

import com.tencent.bkrepo.archive.job.Cancellable
import com.tencent.bkrepo.common.service.shutdown.ServiceShutdownHook
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider

/**
 * 归档服务关机相关配置
 * */
class ArchiveShutdownConfiguration(cancellable: ObjectProvider<Cancellable>) {

    init {
        ServiceShutdownHook.add {
            cancellable.stream().forEach {
                it.cancel()
                logger.info("Shutdown job[${it.javaClass.simpleName}].")
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ArchiveShutdownConfiguration::class.java)
    }
}
