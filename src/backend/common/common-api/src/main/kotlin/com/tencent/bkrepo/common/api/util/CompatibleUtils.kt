package com.tencent.bkrepo.common.api.util

import org.slf4j.Logger
import org.slf4j.LoggerFactory

object CompatibleUtils {

    val logger: Logger = LoggerFactory.getLogger(CompatibleUtils::class.java)

    inline fun <reified T> getValue(new: T, old: T?, property: String): T {
        return if (old != null) {
            logger.warn("Capture deprecated property[$property]")
            old
        } else new
    }
}
