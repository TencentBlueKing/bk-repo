package com.tencent.bkrepo.auth.pojo

abstract class IamBaseReq(
    open var bk_app_code: String,
    open var bk_app_secret: String,
    open var bk_username: String,
    open val bk_token: String = ""
)