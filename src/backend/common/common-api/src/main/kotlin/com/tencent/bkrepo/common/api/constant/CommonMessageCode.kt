package com.tencent.bkrepo.common.api.constant

object CommonMessageCode {
    const val SUCCESS = 0 // 成功
    const val SYSTEM_ERROR = 2100001 // 系统内部繁忙，请稍后再试
    const val PARAMETER_IS_NULL = 2100002 // {0}不能为空
    const val PARAMETER_IS_EXIST = 2100003 // {0}已经存在，请换一个再试
    const val PARAMETER_IS_INVALID = 2100004 // {0}为非法数据
    const val OAUTH_TOKEN_IS_INVALID = 2100005 // 无效的token，请先oauth认证
    const val PERMISSION_DENIED = 2100006 // 无权限{0}
    const val ERROR_SERVICE_NO_FOUND = 2100007 // "找不到任何有效的{0}服务提供者"
    const val ELEMENT_NOT_FOUND = 2100008 // "访问的资源{0}不存在"
    const val ELEMENT_CANNOT_BE_MODIFIED = 2100009 // "资源无法被编辑"
}
