package com.tencent.bkrepo.pypi

import com.tencent.bkrepo.common.service.MicroService
import org.springframework.boot.runApplication

/**
 * 微服务启动类
 *
 * @author: carrypan
 * @date: 2019/12/4
 */
@MicroService
class PypiApplication

fun main(args: Array<String>) {
    runApplication<PypiApplication>(*args)
}
