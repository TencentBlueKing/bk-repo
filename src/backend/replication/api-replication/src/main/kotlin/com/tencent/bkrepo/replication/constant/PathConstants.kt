/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.replication.constant

// oci url
const val OCI_BLOB_URL = "%s/blobs/%s"
const val OCI_MANIFEST_URL = "%s/manifests/%s"
const val OCI_BLOBS_UPLOAD_FIRST_STEP_URL = "%s/blobs/uploads/"


// blobs upload
const val BOLBS_UPLOAD_FIRST_STEP_URL = "/replica/{projectId}/{repoName}/blobs/uploads"
const val BOLBS_UPLOAD_SECOND_STEP_URL = "/replica/{projectId}/{repoName}/blobs/uploads/{uuid}"

const val BOLBS_UPLOAD_FIRST_STEP_URL_STRING = "/replica/%s/%s/blobs/uploads/"

const val BLOB_PULL_URI = "/replica/blob/pull"
const val BLOB_PUSH_URI = "/replica/blob/push"
const val BLOB_CHECK_URI = "/replica/blob/check"
