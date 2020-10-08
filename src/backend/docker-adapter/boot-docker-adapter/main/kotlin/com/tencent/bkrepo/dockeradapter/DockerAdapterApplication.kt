package com.tencent.bkrepo.rpm

import com.tencent.bkrepo.common.service.MicroService
import org.springframework.boot.runApplication

/**
 * 微服务启动类
 */
@MicroService
class DockerAdapterApplication

fun main(args: Array<String>) {
    runApplication<DockerAdapterApplication>(*args)
}
