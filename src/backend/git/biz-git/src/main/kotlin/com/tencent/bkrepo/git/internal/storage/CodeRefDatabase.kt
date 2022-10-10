package com.tencent.bkrepo.git.internal.storage

import org.eclipse.jgit.internal.storage.dfs.DfsReftableDatabase

/**
 * git ref数据库
 * */
class CodeRefDatabase(val repository: CodeRepository) : DfsReftableDatabase(repository)
