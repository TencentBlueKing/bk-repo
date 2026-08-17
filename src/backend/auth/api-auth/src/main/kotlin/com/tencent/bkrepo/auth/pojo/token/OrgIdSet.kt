package com.tencent.bkrepo.auth.pojo.token

/**
 * 将组织 ID 去空白、丢掉空串后转为集合。
 */
fun Collection<String>.normalizeOrgIds(): Set<String> {
    return map { it.trim() }.filter { it.isNotEmpty() }.toSet()
}

/**
 * 用户组织 scopeValue 与授权组织 ID 集合求交；授权集合为空时为 false。
 */
fun List<OrgScope>.matchesAuthorizedOrgIds(authorizedOrgIds: Collection<String>): Boolean {
    val ids = authorizedOrgIds.normalizeOrgIds()
    if (ids.isEmpty()) {
        return false
    }
    return any { it.scopeValue.trim() in ids }
}
