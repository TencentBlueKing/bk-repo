package com.tencent.bkrepo.git.interceptor.devx

/**
 * 云研发配置
 * */
data class DevxProperties(
    /**
     * 是否开启云研发相关配置
     * */
    var enabled: Boolean = false,
    /**
     * apigw app code
     * */
    var appCode: String = "",
    /**
     * apigw app secret
     * */
    var appSecret: String = "",
    /**
     * 查询云研发工作空间的URL
     * */
    var workspaceUrl: String = "",
)
