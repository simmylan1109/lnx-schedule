package com.lnx.app.core.designsystem

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lnx.app.R
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 把自适应启动图标按系统的合成规则渲染成 png(M8 验收用)。
 *
 * 为什么要自己渲染:启动器会缓存图标,重装后桌面还是老图;而且 Android 13+ 的"主题化图标"
 * 会拿 monochrome 层重新着色,在桌面上根本看不到真实配色。这里直接按规则画:
 * 108×108 的图层里,**中间 72×72 映射到图标可见区**,外层 18 是出血区。
 * 顺带把圆形遮罩也画出来,预览"被裁成圆的"长什么样。
 *
 * 产出的 png 落在 `/data/local/tmp/`,用 `adb pull` 取回当验收证据。
 */
@RunWith(AndroidJUnit4::class)
class LauncherIconRenderTest {

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    private companion object {
        const val TAG = "lnx-icon"
    }

    private fun render(path: String, circular: Boolean) {
        val visible = 288
        val scale = visible / 72f // 108 图层里只有中间 72 是可见区
        val offset = -(108 - 72) / 2f * scale

        val bmp = Bitmap.createBitmap(visible, visible, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        if (circular) {
            val clip = Path().apply { addCircle(visible / 2f, visible / 2f, visible / 2f, Path.Direction.CW) }
            canvas.clipPath(clip)
        }
        canvas.translate(offset, offset)
        canvas.scale(scale, scale)
        listOf(R.drawable.ic_launcher_background, R.drawable.ic_launcher_foreground).forEach { id ->
            ContextCompat.getDrawable(ctx, id)!!.apply {
                setBounds(0, 0, 108, 108)
                draw(canvas)
            }
        }
        // 圆形遮罩的辅助线,直观看出血区在哪
        canvas.setMatrix(null)
        if (!circular) {
            canvas.drawCircle(
                visible / 2f,
                visible / 2f,
                visible / 2f * 0.917f,
                Paint().apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 3f
                    color = 0x33000000
                },
            )
        }

        // 走 logcat 而不是写文件:仪器测试跑完 Gradle 会卸载 App,
        // 写进 App 目录的 png 会跟着被删;写 /sdcard 又受分区存储限制。
        // base64 分块打日志最省事,也确实是 Android 自己渲染出来的像素。
        val bytes = java.io.ByteArrayOutputStream()
            .also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            .toByteArray()
        val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        android.util.Log.i(TAG, "BEGIN $path $b64.length")
        b64.chunked(3000).forEach { android.util.Log.i(TAG, it) }
        android.util.Log.i(TAG, "END $path")
        assertTrue("图标没渲染出来", bytes.isNotEmpty())
    }

    @Test
    fun 渲染方形与圆形两张预览() {
        render("lnx-icon-square.png", circular = false)
        render("lnx-icon-circle.png", circular = true)
    }
}
