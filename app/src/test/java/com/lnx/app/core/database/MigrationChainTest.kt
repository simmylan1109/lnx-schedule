package com.lnx.app.core.database

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 迁移链闸门。防的是**发版那一刻才会炸、而且炸在别人手机上**的那种事:
 * 改了表结构、涨了 [DbVersion.CURRENT],却忘了写迁移或忘了导出 schema。
 *
 * 仪器测试能验"已写的迁移对不对"(MigrationTestHelper 拿 json 比对),但验不了
 * "该写的迁移是不是都在"。这里补上后者 —— 它只需要读一行数字,不需要数据库。
 *
 * 三条判据:
 * 1. 链从 1 起步,每一段首尾相接,不能有洞(有 4→5 没有 3→4,老用户从 3 升不上去);
 * 2. 链的终点必须正好等于 [DbVersion.CURRENT](涨了版本忘写迁移 → 红);
 * 3. `app/schemas/` 下 1..CURRENT 的 json 都在(忘了让 Room 导出 → 红)。
 */
class MigrationChainTest {

    private val CURRENT = DbVersion.CURRENT

    private val migrations = LnxMigrations.ALL.sortedBy { it.startVersion }

    @Test
    fun 迁移链必须从1起步且首尾相接没有洞() {
        assertTrue("一条迁移都没有,却把库版本定成 $CURRENT", migrations.isNotEmpty())
        assertEquals(
            "最早一段迁移必须从 v1 起,v1 是发给第一批用户的库,永远有人停在它上面",
            1, migrations.first().startVersion,
        )
        migrations.zipWithNext { earlier, later ->
            assertEquals(
                "迁移链在 ${earlier.endVersion} → ${later.startVersion} 断开:" +
                    "老用户停在 v${earlier.endVersion} 升不上来",
                earlier.endVersion, later.startVersion,
            )
        }
    }

    @Test
    fun 同一段起止版本不能登记两次() {
        val duplicates = migrations.groupBy { it.startVersion }.filterValues { it.size > 1 }.keys
        assertTrue("这些起始版本登记了多条迁移: $duplicates", duplicates.isEmpty())
    }

    @Test
    fun 迁移链终点必须等于数据库版本() {
        assertEquals(
            "库版本已经是 $CURRENT,但迁移链只走到 " +
                "${migrations.last().endVersion} —— 升级会抛 " +
                "\"A migration from X to $CURRENT was required but not found\"",
            CURRENT, migrations.last().endVersion,
        )
    }

    @Test
    fun 每一个库版本都必须留下schema快照() {
        val dir = schemaDir()
        for (version in 1..CURRENT) {
            val file = File(dir, "$version.json")
            assertTrue(
                "缺 ${file.path}:Room 没导出 v$version 的 schema。" +
                    "MigrationTestHelper 靠这些 json 建旧库,缺一个,那条仪器测试就红",
                file.isFile,
            )
        }
    }

    /** 定位 `app/schemas/…`。Gradle 默认在 module 目录跑单测,IDE 可能给仓库根,都试一遍。 */
    private fun schemaDir(): File {
        val leaf = "schemas${File.separator}com.lnx.app.core.database.LnxDatabase"
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            for (candidate in listOf(File(dir, leaf), File(dir, "app${File.separator}$leaf"))) {
                if (candidate.isDirectory) return candidate
            }
            dir = dir.parentFile
        }
        throw AssertionError(
            "从 ${System.getProperty("user.dir")} 往上找不到 $leaf," +
                "schema 快照没进仓库,MigrationTestHelper 没法用",
        )
    }
}
