package com.tencent.bkrepo.common.notify.api.bkci

import com.tencent.bkrepo.common.metadata.annotation.Sensitive
import com.tencent.bkrepo.common.metadata.handler.MaskPartString
import com.tencent.bkrepo.common.notify.api.NotifyChannelCredential
import io.swagger.v3.oas.annotations.media.Schema


@Schema(title = "bkci通知发送渠道凭据")
data class BkciChannelCredential(
    override var name: String = "",
    override var default: Boolean = false,
    @get:Schema(title = "蓝鲸应用appCode")
    var appCode: String,
    @get:Schema(title = "蓝鲸应用appSecret")
    @field:Sensitive(handler = MaskPartString::class)
    var appSecret: String,
) : NotifyChannelCredential(name, TYPE, default) {
    companion object {
        const val TYPE = "bkci"
    }
}
