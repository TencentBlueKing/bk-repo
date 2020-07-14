package com.tencent.bkrepo.rpm

import com.tencent.bkrepo.common.service.MicroService
import org.springframework.boot.runApplication

/**
 * 仓库微服务启动类
 *
 * @author: carrypan
 * @date: 2019-09-10
 */
@MicroService
class RpmApplication

fun main(args: Array<String>) {
    runApplication<RpmApplication>(*args)
}
