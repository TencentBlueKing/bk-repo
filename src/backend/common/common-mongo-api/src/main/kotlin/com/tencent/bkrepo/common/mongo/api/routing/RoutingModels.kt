package com.tencent.bkrepo.common.mongo.api.routing

import org.springframework.data.mongodb.core.MongoTemplate

data class RoutedTemplate(
    val instanceId: String,
    val primary: MongoTemplate,
    val secondary: MongoTemplate?,
)

data class InstanceBinding(
    val instanceId: String,
    val tier: InstanceTier = InstanceTier.HEAVY,
    val bindingType: BindingType = BindingType.DEDICATED,
    val businessId: String? = null,
    val projectIds: Set<String> = emptySet(),
)

data class RouteTarget(
    val ruleName: String? = null,
    val instanceName: String? = null,
    val useDefault: Boolean = false,
)

data class ReadRoute(
    val template: MongoTemplate,
    val fallbackTemplate: MongoTemplate? = null,
    val fallbackToDefault: Boolean = false,
)

/**
 * 写操作路由结果。
 * @param primary  主路径模板（先写）
 * @param secondary 副路径模板（异步写，null 表示单写）
 */
data class WriteRoute(
    val primary: MongoTemplate,
    val secondary: MongoTemplate? = null,
    val secondaryTarget: RouteTarget? = null,
    val fallbackTemplate: MongoTemplate? = null,
    val fallbackToDefault: Boolean = false,
    val routingKey: String? = null,
    val ruleName: String? = null,
    val isDefaultInstance: Boolean = false,
    /** 模式二双写：副路径同步写 Default（§3.6.3） */
    val syncSecondaryWrite: Boolean = false,
)
