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

package com.tencent.bkrepo.oci.constant

import com.tencent.bkrepo.common.api.message.MessageCode

enum class OciMessageCode(private val key: String) : MessageCode {
    OCI_FILE_ALREADY_EXISTS("oci.file.already.exists"),
    OCI_FILE_NOT_FOUND("oci.file.not.found"),
    OCI_FILE_UPLOAD_FORBIDDEN("oci.file.upload.forbidden"),
    OCI_REPO_NOT_FOUND("oci.repo.not.found"),
    OCI_DELETE_RULES("oci.delete.rules"),
    OCI_VERSION_NOT_FOUND("oci.version.not.found"),
    OCI_MANIFEST_INVALID("oci.manifest.invalid"),
    OCI_DIGEST_INVALID("oci.digest.invalid"),
    OCI_MANIFEST_SCHEMA1_NOT_SUPPORT("oci.manifest.schema1.not.support"),
    OCI_REMOTE_CONFIGURATION_ERROR("oci.remote.configuration.error"),
    OCI_REMOTE_CREDENTIALS_INVALID("oci.remote.credentials.invalid")
    ;
    override fun getBusinessCode() = ordinal + 1
    override fun getKey() = key
    override fun getModuleCode() = 19
}
