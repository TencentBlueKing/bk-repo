package com.tencent.bkrepo.executor.util

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 生成扫描任务运行时
 */
object TaskIdUtil {

    fun build(): String {
        val current = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyyMMddHH")
        return current.format(formatter)
    }
}
