package com.tencent.bkrepo.migrate

import com.tencent.bkrepo.common.service.condition.MicroService
import org.springframework.boot.runApplication

/**
 * 通用文件微服务启动类
 */
@MicroService
class MigrateApplication

fun main(args: Array<String>) {
    runApplication<MigrateApplication>(*args)
}
