/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.oci.test

// oci 测试用例以及脚本地址 https://github.com/opencontainers/distribution-spec/tree/main/conformance

// # Registry details
// export OCI_ROOT_URL="http://registry.me:25907"
// export OCI_NAMESPACE="test/test-oci/test"
// export OCI_CROSSMOUNT_NAMESPACE="myorg/other"
// export OCI_USERNAME="admin"
// export OCI_PASSWORD="password"
//
// # Which workflows to run
// export OCI_TEST_PULL=1
// # Optional: set to prevent automatic setup
// OCI_MANIFEST_DIGEST=sha256:52dbbcb6185d373e773ff70e90ca4c99a63f43af8b091be3662ebe25d9371fa7
// OCI_TAG_NAME=emptylayer
// OCI_BLOB_DIGEST=sha256__6e766a49e512e0ba0bc935e2aacd3e5a4a34add17f83afc4c9e669c70241cd48
//
// export OCI_TEST_PUSH=1
// export OCI_TEST_CONTENT_DISCOVERY=1
// export OCI_TEST_CONTENT_MANAGEMENT=1
//
// # Extra settings
// export OCI_HIDE_SKIPPED_WORKFLOWS=0
// export OCI_DEBUG=0
// export OCI_DELETE_MANIFEST_BEFORE_BLOBS=0
