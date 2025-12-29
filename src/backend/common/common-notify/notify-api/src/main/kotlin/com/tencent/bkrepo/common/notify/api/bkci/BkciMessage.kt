package com.tencent.bkrepo.common.notify.api.bkci

import com.tencent.bkrepo.common.notify.api.NotifyMessage
import com.tencent.bkrepo.common.notify.api.weworkbot.MessageBody

/**
 * 通过蓝盾发送消息，最终也会转化为企业微信机器人消息
 */
class BkciMessage(
    /**
     * 消息体
     */
    val body: MessageBody,
    /**
     * 消息接收者
     */
    val chatIds: Set<String>? = null,
    /**
     * 消息接收人类型
     */
    val receiverType: WeworkReceiverType = WeworkReceiverType.single,
) : NotifyMessage(BkciChannelCredential.TYPE)