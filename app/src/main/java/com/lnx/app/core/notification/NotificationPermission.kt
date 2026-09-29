package com.lnx.app.core.notification

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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

    /**
     * 请求通知权限。已授予则直接返回。
     *
     * 注意:这里**没有**"已永久拒绝就不再问"的判断 —— Android 的系统行为是第二次起
     * requestPermissions 会被静默拒绝(不再弹框),所以观感上不会反复打扰。
     * 真正的"去开启"引导入口归设置页(M6 接线),届时用 shouldShowRequestPermissionRationale
     * 判断用户是否拒绝过、给出跳系统设置的入口。
     */
    fun request(activity: Activity) {
        if (!isRequired() || isGranted(activity)) return
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            REQUEST_CODE,
        )
    }
}

/** Compose 里拿 Activity:LocalContext 通常就是 Activity,被包过一层也能剥出来 */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
