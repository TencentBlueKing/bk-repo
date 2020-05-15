package com.tencent.bkrepo.commom.notify

import com.tencent.bkrepo.common.notify.service.DevopsNotify
import jdk.nashorn.internal.ir.annotations.Ignore
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("通知测试")
internal class NodeUtilsTest {
    private var devopsServer = "http://dev.devops.oa.com"

    @Ignore
    @Test
    fun sendEmail() {
        DevopsNotify(devopsServer).sendMail(
            receivers = listOf("necrohuang"),
            ccs = listOf("necrohuang"),
            title = "title",
            body = "body"
        )
    }

    @Ignore
    @Test
    fun sendSms() {
        DevopsNotify(devopsServer).sendSms(
            receivers = listOf("necrohuang"),
            body = "body"
        )
    }

    @Ignore
    @Test
    fun sendWework() {
        DevopsNotify(devopsServer).sendWework(
            receivers = listOf("necrohuang"),
            title = "title",
            body = "body"
        )
    }

    @Ignore
    @Test
    fun sendWechat() {
        DevopsNotify(devopsServer).sendWechat(
            receivers = listOf("necrohuang"),
            body = "body"
        )
    }
}


