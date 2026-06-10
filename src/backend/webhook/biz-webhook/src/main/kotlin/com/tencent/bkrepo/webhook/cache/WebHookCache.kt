package com.tencent.bkrepo.webhook.cache

import com.google.common.cache.CacheBuilder
import com.tencent.bkrepo.webhook.constant.AssociationType
import com.tencent.bkrepo.webhook.model.TWebHook
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class WebHookCache {

    // key: "$associationType:$associationId", 缓存空结果以消除无效 DB 查询
    private val cache = CacheBuilder.newBuilder()
        .maximumSize(20000)
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .build<String, List<TWebHook>>()

    fun get(
        associationType: AssociationType,
        associationId: String,
        loader: () -> List<TWebHook>
    ): List<TWebHook> = cache.get("$associationType:$associationId", loader)

    fun invalidate(associationType: AssociationType, associationId: String) {
        cache.invalidate("$associationType:$associationId")
    }
}
