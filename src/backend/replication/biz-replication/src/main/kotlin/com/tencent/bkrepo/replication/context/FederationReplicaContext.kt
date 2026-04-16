package com.tencent.bkrepo.replication.context

/**
 * 联邦同步写入上下文，用于标记当前线程正在处理来自联邦同步的写入请求。
 *
 * 注意：此 ThreadLocal 仅在同一进程内有效。当 ArtifactReplicaController 通过 Feign 调用
 * auth 服务（跨进程）时，标记通过 X-Federation-Write: true 请求头传递。
 * ReplicationConfigurer 中注册的 Feign RequestInterceptor 会在 isFederationWrite() 为 true
 * 时自动注入该请求头；auth 侧的 FederationWriteInterceptor 读取该头并设置 FederationWriteContext，
 * 使各 auth 服务的 publishEvent 方法跳过事件发布，防止事件风暴。
 */
object FederationReplicaContext {

    private val FEDERATION_WRITE = ThreadLocal<Boolean>()

    fun markAsFederationWrite() {
        FEDERATION_WRITE.set(true)
    }

    fun isFederationWrite(): Boolean {
        return FEDERATION_WRITE.get() == true
    }

    fun clear() {
        FEDERATION_WRITE.remove()
    }
}
