package com.tencent.bkrepo.dockerapi

import com.tencent.bkrepo.common.service.MicroService
import org.springframework.boot.runApplication

/**
 * 微服务启动类
 */
@MicroService
class DockerApiApplication

fun main(args: Array<String>) {
    runApplication<DockerApiApplication>(*args)
}
