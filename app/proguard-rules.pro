# lnx 发布构建的 R8 规则(M9 加)
#
# 原则:能靠官方 consumer rules 解决的都不在这里写(AOSP 的 AndroidX、Hilt、Room 都自带,
# 它们的规则随依赖自动进 R8)。这里补两类"库看不见、但我们靠反射/字符串活着"的东西,
# 以及一条官方规则没覆盖到的枚举名。
#
# 验证方式不是靠读规则,而是 M9 装 release 包在真机跑完整走查 —— 规则写错只有那种
# 跑法才照得出来(见 docs/RELEASE.md 的"发布前必跑")。

# ── 1. 枚举(必要,别删)─────────────────────────────────────────────────
# 事件优先级这些枚举的**名字**会进备份 JSON(BackupModels 里存的是 enum.name,
# 不是序号)。R8 的默认枚举规则只保 values()/valueOf(),**不保常量本身的名字**
# —— 一改名,拿旧备份导入到新版本就认不出来了。
-keepclassmembers enum com.lnx.app.** {
    *;
}

# ── 2. 备份 JSON 的序列化器 ───────────────────────────────────────────────
# kotlinx.serialization 自 1.5 起自带 consumer rules,正常情况下这三条是冗余的。
# 留着是因为代价只有几行配置,而漏掉的代价是"用户的备份读不回来"——
# 备份是 M7 的核心功能,且这个风险**在开发机上永远暴露不出来**。
-keepclasseswithmembers,includedescriptorclasses class com.lnx.app.core.backup.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.lnx.app.core.backup.** {
    *** Companion;
}
-keepclassmembers class com.lnx.app.core.backup.** {
    *** Companion;
}

# ── 3. 崩溃栈可读性 ──────────────────────────────────────────────────────
# 用户报"一打开就闪退"时,我们要看的是类名和方法名,不是 a.b.c。留一行源码/行号,
# 代价是包大一点点,值。
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
