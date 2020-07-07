package com.tencent.bkrepo.rpm

const val PACKAGE_START_MARK = "  <package type=\"rpm\">"
const val PACKAGE_END_MARK = "</package>\n"
// xml中 metadata
const val METADATA_PREFIX = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n<metadata xmlns=\"http://linux.duke.edu/metadata/common\" xmlns:rpm=\"http://linux.duke.edu/metadata/rpm\" packages=\"1\">\n" +
        "  "
const val METADATA_SUFFIX = "</metadata>"
