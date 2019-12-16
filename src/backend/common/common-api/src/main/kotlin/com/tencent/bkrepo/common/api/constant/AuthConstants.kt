package com.tencent.bkrepo.common.api.constant

const val AUTH_HEADER_USER_ID: String = "X-BKREPO-UID"


object AuthMessageCode {
    const val AUTH_DUP_UID = 2502001    // duplicate uid
    const val AUTH_USER_NOT_EXIST = 2502002    // user not exist
    const val AUTH_DELETE_USER_FAILED = 2502003    // delete user failed
    const val AUTH_USER_TOKEN_ERROR = 2502004     // auth user token error
    const val AUTH_ROLE_NOT_EXIST  = 2502005     // role not exist
    const val AUTH_DUP_RID = 2502006    // duplicate rid
    const val AUTH_DUP_PERMNAME = 2502007    // duplicate permission name
    const val AUTH_PERMISSION_NOT_EXIST = 2502008    // permission not exist
    const val AUTH_PERMISSION_FAILED = 2502009    // auth permission failed
    const val AUTH_USER_PERMISSION_EXIST = 2502010    // user permission exist
    const val AUTH_ROLE_PERMISSION_EXIST = 2502011    // role permission exist
}
