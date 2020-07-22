package com.tencent.bkrepo.generic

import com.tencent.bkrepo.common.service.MicroService
import org.springframework.boot.runApplication

/**
 * 通用文件微服务启动类
 *
 * @author: carrypan
 * @date: 2019-09-10
 */
@MicroService
class GenericApplication

fun main(args: Array<String>) {
    runApplication<GenericApplication>(*args)
}
