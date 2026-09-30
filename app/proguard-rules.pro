# lnx 发布构建的 R8 规则(M9 加)
#
# 原则:能靠官方 consumer rules 解决的都不在这里写(AOSP 的 AndroidX、Hilt、Room、
# kotlinx-serialization 都自带,它们的规则随依赖自动进 R8)。这里只补两类
# "库看不见、但我们靠反射/字符串活着"的东西。
#
# 验证方式不是靠读规则,而是 M9 装 release 包在真机跑完整走查 —— 规则写错只有那种
# 跑法才照得出来(见 docs/RELEASE.md 的"发布前必跑")。

# ── 1. 枚举 ───────────────────────────────────────────────────────────────
# 事件优先级、主题槽位这些枚举的**名字**会进备份 JSON(BackupModels 里存的是
# enum.name,不是序号)。R8 默认会把用不到的枚举常量改名,一改名,
# 拿旧备份导入到新版本就认不出来了。保险起见整个 app 包的枚举成员全留。
-keepclassmembers enum com.lnx.app.** {
    *;
}

# ── 2. 备份 JSON 的序列化器 ───────────────────────────────────────────────
# kotlinx.serialization 自己会保留 `$$serializer` 类,但它不保证你手写的
# @Serializable 类里的 Companion 和 serializer() 方法活下来 —— 备份/导入是
# M7 的核心功能,这里点名留下。
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
