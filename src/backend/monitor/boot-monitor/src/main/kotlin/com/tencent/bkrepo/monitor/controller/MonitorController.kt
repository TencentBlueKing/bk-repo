package com.tencent.bkrepo.monitor.controller

import com.tencent.bkrepo.monitor.config.MonitorProperties
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/monitor")
class MonitorController(
    private val monitorProperties: MonitorProperties
) {
    @GetMapping("config")
    fun getConfig() = Mono.just(monitorProperties)
}
