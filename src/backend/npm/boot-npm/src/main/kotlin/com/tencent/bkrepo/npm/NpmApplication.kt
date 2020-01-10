package com.tencent.bkrepo.npm

import com.tencent.bkrepo.common.service.MicroService
import org.springframework.boot.runApplication

/**
 * 通用文件微服务启动类
 *
 * @author: charis
 * @date: 2019-12-10
 */
@MicroService
class NpmApplication

fun main(args: Array<String>) {
    runApplication<NpmApplication>(*args)
}
