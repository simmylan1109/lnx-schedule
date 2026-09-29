package com.lnx.app.core.notification

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * 通知权限(spec §3.8):Android 13+ 需要 `POST_NOTIFICATIONS`。
 * 被拒则提醒静默失效,但 App 不能崩,设置页给"去开启"入口(M6 接线)。
 */
object NotificationPermission {

    const val REQUEST_CODE = 1001

    fun isRequired(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun isGranted(context: Context): Boolean =
        !isRequired() ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** 已拒绝过就别再自动弹系统框(spec §3.8:不反复打扰),引导入口由设置页承担 */
    fun request(activity: Activity) {
        if (!isRequired() || isGranted(activity)) return
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            REQUEST_CODE,
        )
    }
}
