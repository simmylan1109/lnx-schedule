package com.lnx.app

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * 让仪器测试跑在 HiltTestApplication 上,便于 @HiltAndroidTest 注入测试替身。
 *
 * 同时在启动时预授权通知权限:Android 13+ 通知权限默认不给,而 App 在"保存带提醒的事件"
 * 那一刻会弹系统权限框(spec §3.8 的语境化索权)。这个框会盖住整个首屏,后续 Compose 断言
 * 全部报 "No compose hierarchies found" —— 权限流本身另有专项测试,其余用例不该被系统弹窗打断。
 */
class HiltTestRunner : AndroidJUnitRunner() {

    override fun newApplication(
        classLoader: ClassLoader?,
        className: String?,
        context: Context?,
    ): Application = super.newApplication(classLoader, HiltTestApplication::class.java.name, context)

    override fun onStart() {
        grantNotificationPermission()
        super.onStart()
    }

    private fun grantNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        try {
            val pfd = uiAutomation.executeShellCommand(
                "pm grant com.lnx.app android.permission.POST_NOTIFICATIONS",
            )
            // 读到 EOF 才算命令执行完毕,否则下面的测试可能在授权生效前就跑起来
            java.io.FileInputStream(pfd.fileDescriptor).use { input ->
                val buffer = ByteArray(1024)
                while (input.read(buffer) != -1) {
                    // drain
                }
            }
            pfd.close()
        } catch (_: Exception) {
            // 授权失败不吞掉测试:拿不到权限的用例会照常失败并暴露出来
        }
    }
}
