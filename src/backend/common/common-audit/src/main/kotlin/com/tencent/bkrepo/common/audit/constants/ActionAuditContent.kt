/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.common.audit.constants

import com.tencent.bk.audit.constants.AuditAttributeNames.INSTANCE_ID
import com.tencent.bk.audit.constants.AuditAttributeNames.INSTANCE_NAME

@Suppress("MaxLineLength")
object ActionAuditContent {

    private const val CONTENT_TEMPLATE = "[{{$INSTANCE_NAME}}]({{$INSTANCE_ID}})"
    private const val PROJECT_CODE_CONTENT_TEMPLATE = "[{{@PROJECT_CODE}}]"
    const val PROJECT_CODE_TEMPLATE = "@PROJECT_CODE"
    // 项目
    const val PROJECT_CREATE_CONTENT = "create project $CONTENT_TEMPLATE"
    const val PROJECT_EDIT_CONTENT = "update project $CONTENT_TEMPLATE"
    const val PROJECT_VIEW_CONTENT = "get project $CONTENT_TEMPLATE"
    //仓库
    const val REPO_VIEW_CONTENT = "get repo info $CONTENT_TEMPLATE in project $PROJECT_CODE_CONTENT_TEMPLATE"
    const val REPO_CREATE_CONTENT = "create repo info $CONTENT_TEMPLATE in project $PROJECT_CODE_CONTENT_TEMPLATE"
    const val REPO_LIST_CONTENT = "list repo $CONTENT_TEMPLATE in project $PROJECT_CODE_CONTENT_TEMPLATE"
    const val REPO_EDIT_CONTENT = "update repo $CONTENT_TEMPLATE in project $PROJECT_CODE_CONTENT_TEMPLATE"
    const val REPO_DELETE_CONTENT = "delete repo $CONTENT_TEMPLATE in project $PROJECT_CODE_CONTENT_TEMPLATE"
}