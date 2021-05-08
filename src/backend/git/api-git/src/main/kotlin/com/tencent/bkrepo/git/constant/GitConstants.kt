package com.tencent.bkrepo.git.constant

const val REF = "ref"
const val DOT_GIT = ".git"
const val BLANK = ""
const val TMP_FILE_PREFIX = "bkrepo_git_"
const val OBJECT_ID = "objectId"
const val R_REMOTE_ORIGIN = "refs/remotes/origin/"
const val R_HEADS = "refs/heads/"
const val MASTER = "master"
const val CACHE_REF_PATH = ".ref/"
const val PARAMETER_HUBTYPE = "hub_type"
const val PARAMETER_OWNER = "owner"
const val PATH_SYNC = "sync"
const val REDIS_LOCK_KEY_PREFIX = "git:lock:"
const val REDIS_SET_REPO_TO_UPDATE = "git:repo:to_update"

fun convertorLockKey(key: String): String {
    return "$REDIS_LOCK_KEY_PREFIX$key"
}
