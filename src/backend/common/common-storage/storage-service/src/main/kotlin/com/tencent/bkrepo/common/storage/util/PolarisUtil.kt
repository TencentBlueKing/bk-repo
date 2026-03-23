/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.common.storage.util

import org.slf4j.LoggerFactory

/**
 * 北极星服务发现工具类
 *
 * 使用反射调用 polaris-factory-shaded jar 包，避免编译时强依赖
 */
object PolarisUtil {

    private val logger = LoggerFactory.getLogger(PolarisUtil::class.java)

    private const val DISCOVERY_API_FACTORY_CLASS = "com.tencent.polaris.factory.api.DiscoveryAPIFactory"
    private const val GET_ONE_INSTANCE_REQUEST_CLASS = "com.tencent.polaris.api.rpc.GetOneInstanceRequest"

    /**
     * ConsumerAPI 实例，延迟初始化
     */
    private val consumerAPI: Any? by lazy {
        try {
            val factoryClass = Class.forName(DISCOVERY_API_FACTORY_CLASS)
            val createMethod = factoryClass.getMethod("createConsumerAPI")
            createMethod.invoke(null)
        } catch (e: ClassNotFoundException) {
            logger.warn("Polaris SDK not found in classpath, polaris service discovery disabled")
            null
        } catch (e: Exception) {
            logger.error("Failed to create polaris ConsumerAPI", e)
            null
        }
    }

    /**
     * GetOneInstanceRequest 类，延迟加载
     */
    private val requestClass: Class<*>? by lazy {
        try {
            Class.forName(GET_ONE_INSTANCE_REQUEST_CLASS)
        } catch (e: ClassNotFoundException) {
            logger.warn("Polaris GetOneInstanceRequest class not found")
            null
        }
    }

    /**
     * 检查 Polaris SDK 是否可用
     */
    fun isAvailable(): Boolean = consumerAPI != null && requestClass != null

    /**
     * 从北极星获取一个服务实例
     *
     * @param namespace 命名空间
     * @param service 服务名称
     * @return 实例地址，格式为 host:port
     * @throws IllegalStateException 如果 Polaris SDK 不可用或获取实例失败
     */
    fun getOneInstance(namespace: String, service: String): String {
        val api = consumerAPI ?: throw IllegalStateException("Polaris SDK is not available")
        val reqClass = requestClass ?: throw IllegalStateException("Polaris SDK is not available")

        // 创建 GetOneInstanceRequest 实例
        val request = reqClass.getDeclaredConstructor().newInstance()

        // 设置 namespace 和 service
        reqClass.getMethod("setNamespace", String::class.java).invoke(request, namespace)
        reqClass.getMethod("setService", String::class.java).invoke(request, service)

        // 调用 consumerAPI.getOneInstance(request)
        val getOneInstanceMethod = api.javaClass.getMethod("getOneInstance", reqClass)
        val response = getOneInstanceMethod.invoke(api, request)

        // 获取 response.instances
        val getInstancesMethod = response.javaClass.getMethod("getInstances")
        @Suppress("UNCHECKED_CAST")
        val instances = getInstancesMethod.invoke(response) as Array<Any>

        check(instances.isNotEmpty()) {
            "polaris resolve service failed: service [$service]"
        }

        // 获取第一个实例的 host 和 port
        val instance = instances.first()
        val host = instance.javaClass.getMethod("getHost").invoke(instance) as String
        val port = instance.javaClass.getMethod("getPort").invoke(instance) as Int

        if (logger.isDebugEnabled) {
            logger.debug("polaris resolve success: [$host:$port]")
        }
        return "$host:$port"
    }
}
