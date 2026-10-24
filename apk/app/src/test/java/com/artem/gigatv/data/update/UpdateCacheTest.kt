package com.artem.gigatv.data.update

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class UpdateCacheTest {
    @Test fun boundsRepeatedDownloadsAndExpiresOldFiles() {
        val dir = Files.createTempDirectory("update-cache-test").toFile()
        try {
            val unrelated = dir.resolve("other.txt").apply { writeText("keep") }
            repeat(5) { index ->
                pruneUpdateCache(dir, 10_000)
                dir.resolve("gigatv-1-$index.apk").apply {
                    writeText("apk")
                    setLastModified(1_000L + index)
                }
            }
            assertEquals(2, dir.listFiles()!!.count { it.extension == "apk" })
            pruneUpdateCache(dir, 2 * 24 * 60 * 60 * 1000L)
            assertEquals(listOf(unrelated), dir.listFiles()!!.toList())
        } finally {
            dir.listFiles().orEmpty().forEach { it.delete() }
            dir.delete()
        }
    }
}
