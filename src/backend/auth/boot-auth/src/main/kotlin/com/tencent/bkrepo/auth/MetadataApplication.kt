package com.tencent.bkrepo.auth

import com.tencent.bkrepo.common.service.MicroService
import org.springframework.boot.runApplication

/**
 * 元数据微服务启动类
 *
 * @author: carrypan
 * @date: 2019-09-10
 */
@MicroService
class MetadataApplication

fun main(args: Array<String>) {
    runApplication<MetadataApplication>(*args)
}
