package com.tencent.bkrepo.binary

import com.tencent.bkrepo.common.service.MicroService
import org.springframework.boot.runApplication

/**
 * 二进制微服务启动类
 *
 * @author: carrypan
 * @date: 2019-09-10
 */
@MicroService
class BinaryApplication

fun main(args: Array<String>) {
    runApplication<BinaryApplication>(*args)
}
