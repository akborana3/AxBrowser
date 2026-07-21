package com.akay.core.data.network

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OkHttpProvider @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    val browserClient: OkHttpClient = okHttpClient

    val downloadClient: OkHttpClient = okHttpClient.newBuilder()
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
}
