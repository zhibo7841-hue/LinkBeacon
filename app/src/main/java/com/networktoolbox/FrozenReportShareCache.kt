package com.networktoolbox

import java.io.File
import java.util.UUID

/** Small shared delivery primitive; immutable UUID paths, bounded bytes, scoped expiry. */
internal fun writeFrozenReportShare(cacheDir:File,category:String,fileName:String,extensions:Set<String>,
    bytes:ByteArray,now:Long=System.currentTimeMillis(),maxBytes:Int=5*1024*1024):File {
    require(category in setOf("text","web"))
    require(bytes.isNotEmpty() && bytes.size<=maxBytes)
    require(fileName.length in 1..120 && !fileName.startsWith('.') &&
        fileName.all {it.isLetterOrDigit() || it in "._-"} && fileName.substringAfterLast('.') in extensions)
    val root=File(cacheDir,"reports/$category").canonicalFile
    check(root.isDirectory || root.mkdirs())
    root.listFiles()?.filter {dir->dir.isDirectory && runCatching {UUID.fromString(dir.name)}.isSuccess &&
        dir.canonicalFile.parentFile==root && now-dir.lastModified()>TextReportShareCache.RETENTION_MS
    }?.forEach {dir->
        dir.listFiles()?.filter {it.isFile && it.canonicalFile.parentFile==dir.canonicalFile && it.extension in extensions}
            ?.forEach {it.delete()}
        dir.delete()
    }
    val directory=File(root,UUID.randomUUID().toString())
    check(directory.mkdir())
    return File(directory,fileName).apply {writeBytes(bytes);directory.setLastModified(now)}
}
