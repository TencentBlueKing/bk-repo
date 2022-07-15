package com.tencent.bkrepo.auth.pojo.enums;

enum class UserSource(val value: String) {
    PIPELINE("pipeline"),
    PAAS("paas"),
    REPO("repo")
}