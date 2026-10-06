package com.lnx.app.core.di

import android.content.Context
import com.lnx.app.R
import com.lnx.app.core.network.CertificatePinner
import com.lnx.app.core.network.pinnedHttpClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import java.security.cert.X509Certificate
import javax.inject.Singleton

/**
 * 网络层的装配(v0.3 A2):从 `res/raw/server_cert.pem` 读出内置的固定证书,
 * 交给 [pinnedHttpClient] 建出**只认这一张证书**的单例 HttpClient。
 *
 * 证书走资源而不是硬编码字符串:资源会被 aapt 校验、被 shrinker 当成普通资源管理,
 * 而且换证书 = 换一个资源文件,不用动代码。
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun providePinnedCertificate(@ApplicationContext context: Context): X509Certificate =
        context.resources.openRawResource(R.raw.server_cert)
            .use(CertificatePinner::loadPinnedCert)

    @Provides
    @Singleton
    fun providePinnedHttpClient(pinned: X509Certificate): HttpClient =
        pinnedHttpClient(pinned)
}
