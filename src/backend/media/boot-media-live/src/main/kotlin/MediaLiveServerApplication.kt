package com.tencent.bkrepo.media.live

import com.tencent.bkrepo.common.service.condition.MicroService
import org.springframework.boot.runApplication

/**
 * 通用文件微服务启动类
 */
@MicroService
class MediaLiveServerApplication

fun main(args: Array<String>) {
    runApplication<MediaLiveServerApplication>(*args)
}