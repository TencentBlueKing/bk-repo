package com.tencent.bkrepo.rpm

const val INDEXER = "Artifact upload success!"
const val NO_INDEXER = "Artifact upload success, but will not indexing it. " +
        "Because the repo : '%s' repodata_depth is '%d'. " +
        "The request artifactUri : '%s' is equal or lesser than repodata_depth."
