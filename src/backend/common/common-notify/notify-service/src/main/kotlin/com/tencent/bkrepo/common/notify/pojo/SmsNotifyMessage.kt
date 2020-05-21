package com.tencent.bkrepo.common.notify.pojo

import com.tencent.bkrepo.common.notify.pojo.enums.EnumNotifyPriority
import com.tencent.bkrepo.common.notify.pojo.enums.EnumNotifySource
import io.swagger.annotations.ApiModel

@ApiModel("短信消息")
open class SmsNotifyMessage(
    var receivers: MutableSet<String> = mutableSetOf(),
    var body: String = "",
    var sender: String = "",
    var priority: EnumNotifyPriority = EnumNotifyPriority.HIGH,
    var source: EnumNotifySource = EnumNotifySource.BUSINESS_LOGIC
) : BaseMessage()
