package com.tencent.bkrepo.auth.context

/**
 * 联邦同步写入上下文，用于在 auth 服务内标记当前请求来源于联邦复制写操作。
 *
 * replication 模块的 ArtifactReplicaController 通过 Feign 调用 auth 服务时，
 * 会携带 X-Federation-Write: true 请求头。
 * FederationWriteInterceptor 读取该 header 并调用 markAsFederationWrite()，
 * 使 auth 服务的 publishEvent 方法跳过事件发布，从而避免事件风暴。
 */
object FederationWriteContext {

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
