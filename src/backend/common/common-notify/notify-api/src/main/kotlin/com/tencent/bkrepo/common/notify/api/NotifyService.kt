package com.tencent.bkrepo.common.notify.api

interface NotifyService {
    fun sendMail(receivers: List<String>, ccs: List<String>, title: String, body: String)

    fun sendSms(receivers: List<String>, body: String)

    fun sendWework(receivers: List<String>, title: String, body: String)

    fun sendWechat(receivers: List<String>, body: String)
}
