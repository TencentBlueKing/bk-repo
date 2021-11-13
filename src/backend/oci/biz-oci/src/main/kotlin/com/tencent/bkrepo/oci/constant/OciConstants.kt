/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.tencent.bkrepo.oci.constant

const val OCI_API_PREFIX = "v2"

const val DOCKER_HEADER_API_VERSION = "Docker-Distribution-Api-Version"
const val DOCKER_API_VERSION = "registry/2.0"
const val DOCKER_CONTENT_DIGEST = "Docker-Content-Digest"

// OCIScheme is the URL scheme for OCI-based requests
const val OCI_SCHEME = "oci"

// CREDENTIALS_FILE_BASENAME is the filename for auth credentials file
const val CREDENTIALS_FILE_BASENAME = "registry.json"

// CONFIG_MEDIA_TYPE is the reserved media type for the Helm chart manifest config
const val CONFIG_MEDIA_TYPE = "application/vnd.cncf.helm.config.v1+json"

// CHART_LAYER_MEDIA_TYPE is the reserved media type for Helm chart package content
const val CHART_LAYER_MEDIA_TYPE = "application/vnd.cncf.helm.chart.content.v1.tar+gzip"

// PROV_LAYER_MEDIA_TYPE is the reserved media type for Helm chart provenance files
const val PROV_LAYER_MEDIA_TYPE = "application/vnd.cncf.helm.chart.provenance.v1.prov"

// LEGACY_CHART_LAYER_MEDIA_TYPE is the legacy reserved media type for Helm chart package content.
const val LEGACY_CHART_LAYER_MEDIA_TYPE = "application/tar+gzip"
