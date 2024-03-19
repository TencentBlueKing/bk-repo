package com.tencent.bkrepo.archive.job

import reactor.core.publisher.Mono

// 定义一个泛型接口，T 表示资源类型，R 表示处理后的结果类型
interface ResourceManager<T, R> {
    // 启动资源管理器
    fun start()

    // 处理资源，输入资源类型 T，返回处理结果类型 R 的 Mono
    fun process(resource: T): Mono<R>

    // 判断资源管理器是否正忙
    fun isBusy(): Boolean

    // 停止资源管理器
    fun stop()
}
