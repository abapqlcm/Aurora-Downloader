package com.aurora.downloader.download.transport

import okhttp3.Cache
import okhttp3.CookieJar
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * OkHttp over Cronet: no native binary to ship, and the interceptor chain is
 * what actually makes real downloads work (cookies, retries, auth, logging).
 */
object OkHttpFactory {

    fun create(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        return OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)          // never time out a big file
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor(logging)
            .build()
    }

    /** Per-download client carrying that download's cookies + user agent. */
    fun perDownload(
        base: OkHttpClient,
        cookieHeader: String?,
        userAgent: String?,
        referer: String?
    ): OkHttpClient {
        if (cookieHeader.isNullOrBlank() && userAgent.isNullOrBlank() && referer.isNullOrBlank()) {
            return base
        }
        val headerInjector = Interceptor { chain ->
            val request = chain.request().newBuilder()
                .apply {
                    if (!cookieHeader.isNullOrBlank()) header("Cookie", cookieHeader)
                    if (!userAgent.isNullOrBlank()) header("User-Agent", userAgent)
                    if (!referer.isNullOrBlank()) header("Referer", referer)
                }
                .build()
            chain.proceed(request)
        }
        return base.newBuilder()
            .addInterceptor(headerInjector)
            .build()
    }
}
