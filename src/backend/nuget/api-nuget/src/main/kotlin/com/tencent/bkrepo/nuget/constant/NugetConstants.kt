package com.tencent.bkrepo.nuget.constant

const val REPO_TYPE = "NUGET"

const val ID = "id"
const val VERSION = "version"
const val LOWER_VERSION = "lowerVersion"
const val UPPER_VERSION = "upperVersion"

const val METADATA = "nuget_metadata"

const val NUGET_V3_NOT_FOUND = """
    <Error>
        <Code>BlobNotFound</Code>
        <Message>
            The specified blob does not exist.
        </Message>
    </Error>
"""
