package com.tencent.bkrepo.common.notify.pojo

import com.tencent.bkrepo.common.notify.pojo.enums.EnumEmailFormat
import com.tencent.bkrepo.common.notify.pojo.enums.EnumEmailType
import com.tencent.bkrepo.common.notify.pojo.enums.EnumNotifyPriority
import com.tencent.bkrepo.common.notify.pojo.enums.EnumNotifySource
import io.swagger.annotations.ApiModel

@ApiModel("邮件")
open class EmailNotifyMessage(
    var format: EnumEmailFormat = EnumEmailFormat.PLAIN_TEXT,
    var type: EnumEmailType = EnumEmailType.OUTER_MAIL,
    var receivers: Set<String> = setOf(),
    var cc: Set<String> = setOf(),
    var bcc: Set<String> = setOf(),
    var body: String = "",
    var sender: String = "DevOps",
    var title: String = "",
    var priority: EnumNotifyPriority = EnumNotifyPriority.HIGH,
    var source: EnumNotifySource = EnumNotifySource.BUSINESS_LOGIC
) : BaseMessage()
