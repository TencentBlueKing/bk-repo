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

package com.tencent.bkrepo.common.artifact.audit

import com.tencent.bk.audit.constants.AuditAttributeNames.INSTANCE_ID
import com.tencent.bk.audit.constants.AuditAttributeNames.INSTANCE_NAME

@Suppress("MaxLineLength")
object ActionAuditContent {

    private const val CONTENT_TEMPLATE = "[{{$INSTANCE_NAME}}]({{$INSTANCE_ID}})"
    private const val PROJECT_CODE_CONTENT_TEMPLATE = "[{{@PROJECT_CODE}}]"
    private const val REPO_NAME_CONTENT_TEMPLATE = "[{{@REPO_NAME}}]"
    const val PROJECT_CODE_TEMPLATE = "@PROJECT_CODE"
    const val REPO_NAME_TEMPLATE = "@REPO_NAME"
    const val TOKEN_TEMPLATE = "@TOKEN"
    const val DATE_TEMPLATE = "@DATE"
    const val EXPIRES_DYAS_TEMPLATE = "@EXPIRES_DYAS"
    const val NAME_TEMPLATE = "@NAME"
    const val NEW_PROJECT_CODE_CONTENT_TEMPLATE = "@NEW_PROJECT_CODE"
    const val NEW_REPO_NAME_CONTENT_TEMPLATE = "@NEW_REPO_NAME"
    const val VERSION_TEMPLATE = "@VERSION"

    // 项目
    const val PROJECT_CREATE_CONTENT = "create project $CONTENT_TEMPLATE"
    const val PROJECT_EDIT_CONTENT = "update project $CONTENT_TEMPLATE"
    const val PROJECT_VIEW_CONTENT = "get project $CONTENT_TEMPLATE"

    //仓库
    const val REPO_VIEW_CONTENT = "get repo info $CONTENT_TEMPLATE in project $PROJECT_CODE_CONTENT_TEMPLATE"
    const val REPO_QUOTE_VIEW_CONTENT = "get quote of repo $CONTENT_TEMPLATE in project $PROJECT_CODE_CONTENT_TEMPLATE"
    const val REPO_QUOTE_EDIT_CONTENT = "update quote of repo $CONTENT_TEMPLATE in project $PROJECT_CODE_CONTENT_TEMPLATE"
    const val REPO_EXIST_CHECK_CONTENT = "check repo $CONTENT_TEMPLATE exist in project $PROJECT_CODE_CONTENT_TEMPLATE"
    const val REPO_CREATE_CONTENT = "create repo info $CONTENT_TEMPLATE in project $PROJECT_CODE_CONTENT_TEMPLATE"
    const val REPO_LIST_CONTENT = "list repos in project $PROJECT_CODE_CONTENT_TEMPLATE"
    const val REPO_EDIT_CONTENT = "update repo $CONTENT_TEMPLATE in project $PROJECT_CODE_CONTENT_TEMPLATE"
    const val REPO_DELETE_CONTENT = "delete repo $CONTENT_TEMPLATE in project $PROJECT_CODE_CONTENT_TEMPLATE"
    const val REPO_REPLICATION_CREATE_CONTENT = "create replication task for repo $CONTENT_TEMPLATE in project $PROJECT_CODE_CONTENT_TEMPLATE"
    const val REPO_REPLICATION_EXECUTE_CONTENT = "execute replication task [{{@NAME}}] for repo $CONTENT_TEMPLATE in project $PROJECT_CODE_CONTENT_TEMPLATE"
    const val REPO_PACKAGE_DELETE_CONTENT = "delete package [{{@NAME}}] in repo $CONTENT_TEMPLATE project $PROJECT_CODE_CONTENT_TEMPLATE"
    const val REPO_PACKAGE_VERSION_DELETE_CONTENT = "delete version [{{@VERSION}}] of package [{{@NAME}}] in repo $CONTENT_TEMPLATE project $PROJECT_CODE_CONTENT_TEMPLATE"


    // 节点
    const val NODE_SHARE_CREATE_CONTENT = "create share link for node info $CONTENT_TEMPLATE in repo $PROJECT_CODE_CONTENT_TEMPLATE|$REPO_NAME_CONTENT_TEMPLATE"
    const val NODE_SHARE_DOWNLOAD_CONTENT = "download share node $CONTENT_TEMPLATE with token [{{@TOKEN}}] in repo $PROJECT_CODE_CONTENT_TEMPLATE|$REPO_NAME_CONTENT_TEMPLATE"
    const val NODE_DOWNLOAD_WITH_TOKEN_CONTENT = "download node $CONTENT_TEMPLATE with token [{{@TOKEN}}] in repo $PROJECT_CODE_CONTENT_TEMPLATE|$REPO_NAME_CONTENT_TEMPLATE"
    const val NODE_UPLOAD_WITH_TOKEN_CONTENT = "upload node $CONTENT_TEMPLATE with token [{{@TOKEN}}] in repo $PROJECT_CODE_CONTENT_TEMPLATE|$REPO_NAME_CONTENT_TEMPLATE"

    const val NODE_VIEW_CONTENT = "get node info $CONTENT_TEMPLATE in repo $PROJECT_CODE_CONTENT_TEMPLATE|$REPO_NAME_CONTENT_TEMPLATE"
    const val NODE_CREATE_CONTENT = "create node $CONTENT_TEMPLATE in repo $PROJECT_CODE_CONTENT_TEMPLATE|$REPO_NAME_CONTENT_TEMPLATE"
    const val NODE_DELETE_CONTENT = "delete node $CONTENT_TEMPLATE in repo $PROJECT_CODE_CONTENT_TEMPLATE|$REPO_NAME_CONTENT_TEMPLATE"
    const val NODE_CLEAN_CONTENT = "clean node $CONTENT_TEMPLATE before [{{@DATE}}] in repo $PROJECT_CODE_CONTENT_TEMPLATE|$REPO_NAME_CONTENT_TEMPLATE"
    const val NODE_EXPIRES_EDIT_CONTENT = "set [{{@EXPIRES_DYAS}}] expire days to node $CONTENT_TEMPLATE in repo $PROJECT_CODE_CONTENT_TEMPLATE|$REPO_NAME_CONTENT_TEMPLATE"
    const val NODE_RENAME_CONTENT = "rename node $CONTENT_TEMPLATE to [{{@NAME}}] in repo $PROJECT_CODE_CONTENT_TEMPLATE|$REPO_NAME_CONTENT_TEMPLATE"
    const val NODE_MOVE_CONTENT = "move node $CONTENT_TEMPLATE from repo $PROJECT_CODE_CONTENT_TEMPLATE|$REPO_NAME_CONTENT_TEMPLATE to [{{@NAME}}] in [{{@NEW_PROJECT_CODE}}]|[{{@NEW_REPO_NAME}}]"
    const val NODE_COPY_CONTENT = "copy node $CONTENT_TEMPLATE in repo $PROJECT_CODE_CONTENT_TEMPLATE|$REPO_NAME_CONTENT_TEMPLATE to [{{@NAME}}] in [{{@NEW_PROJECT_CODE}}]|[{{@NEW_REPO_NAME}}]"
    const val NODE_RESTORE_CONTENT = "restore node $CONTENT_TEMPLATE with deleted time [{{@DATE}}] in repo $PROJECT_CODE_CONTENT_TEMPLATE|$REPO_NAME_CONTENT_TEMPLATE"
    const val NODE_METADATA_VIEW_CONTENT = "get metadata of node $CONTENT_TEMPLATE in repo $PROJECT_CODE_CONTENT_TEMPLATE|$REPO_NAME_CONTENT_TEMPLATE"
    const val NODE_METADATA_EDIT_CONTENT = "update metadata of node $CONTENT_TEMPLATE in repo $PROJECT_CODE_CONTENT_TEMPLATE|$REPO_NAME_CONTENT_TEMPLATE"
    const val NODE_METADATA_FORBID_CONTENT = "forbid metadata of node $CONTENT_TEMPLATE in repo $PROJECT_CODE_CONTENT_TEMPLATE|$REPO_NAME_CONTENT_TEMPLATE"
    const val NODE_METADATA_DELETE_CONTENT = "delete metadata of node $CONTENT_TEMPLATE in repo $PROJECT_CODE_CONTENT_TEMPLATE|$REPO_NAME_CONTENT_TEMPLATE"
    const val NODE_UPLOAD_CONTENT = "upload node $CONTENT_TEMPLATE in repo $PROJECT_CODE_CONTENT_TEMPLATE|$REPO_NAME_CONTENT_TEMPLATE"
    const val NODE_DOWNLOAD_CONTENT = "download node $CONTENT_TEMPLATE in repo $PROJECT_CODE_CONTENT_TEMPLATE|$REPO_NAME_CONTENT_TEMPLATE"

}