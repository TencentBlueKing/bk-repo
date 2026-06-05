/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.auth.service.bkdevops

/**
 * "项目成员"探测的三态结果。
 *
 * 用于支撑"残留成员清理"场景下的安全语义：
 * - [IS_MEMBER] / [NOT_MEMBER]：bk-ci 接口明确返回了结果
 * - [UNKNOWN]：bk-ci 接口调用失败/超时，调用方不应基于此值做出"清理"等不可逆决策
 */
enum class MembershipProbeResult {
    IS_MEMBER,
    NOT_MEMBER,
    UNKNOWN,
}
