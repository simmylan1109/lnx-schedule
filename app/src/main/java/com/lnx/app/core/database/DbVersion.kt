package com.lnx.app.core.database

/**
 * 数据库 schema 的当前版本。**单独一个文件**是为了让 [LnxDatabase] 的 `@Database(version = …)`
 * 能引用它 —— KSP 处理 `LnxDatabase` 的注解时,类自己的伴生对象还没成形,引用会报
 * "references a type that is not present"(M9 踩过)。
 *
 * 它和 App 的 `versionCode` 是两码事:那个每次发版都涨,这个只在表结构变了时才涨。
 * 涨它的完整清单写在 [LnxMigrations.ALL] 的注释里。
 */
object DbVersion {
    const val CURRENT: Int = 4
}
