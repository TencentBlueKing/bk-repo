package com.tencent.bkrepo.common.notify.pojo

import com.tencent.bkrepo.common.notify.pojo.enums.EnumNotifyPriority
import com.tencent.bkrepo.common.notify.pojo.enums.EnumNotifySource
import io.swagger.annotations.ApiModel

@ApiModel("rtx消息/企业微信")
open class RtxNotifyMessage(
    var receivers: Set<String> = setOf(),
    var body: String = "",
    var sender: String = "",
    var title: String = "",
    var priority: EnumNotifyPriority = EnumNotifyPriority.HIGH,
    var source: EnumNotifySource = EnumNotifySource.BUSINESS_LOGIC
) : BaseMessage()
