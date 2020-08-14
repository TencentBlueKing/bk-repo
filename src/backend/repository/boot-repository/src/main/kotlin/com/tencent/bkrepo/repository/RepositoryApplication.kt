package com.tencent.bkrepo.repository

import com.tencent.bkrepo.common.service.MicroService
import org.springframework.boot.runApplication

/**
 * 仓库微服务启动类
 */
@MicroService
class RepositoryApplication

fun main(args: Array<String>) {
    runApplication<RepositoryApplication>(*args)
}
