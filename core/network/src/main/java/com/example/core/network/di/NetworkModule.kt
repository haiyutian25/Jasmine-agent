package com.example.core.network.di

import com.example.core.network.BuildConfig
import com.example.core.network.FontDownloadApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Qualifier for the injected Retrofit base URL. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BaseUrl

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /** Tolerates unknown keys so DTOs stay forward-compatible with API changes. */
    private val json = Json {
        ignoreUnknownKeys = true
    }

    /** Single injection point for the API base URL (swap for the real backend here). */
    @Provides
    @Singleton
    @BaseUrl
    fun provideBaseUrl(): String = "https://api.example.com/"

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            // Log request/response lines in debug builds only; release stays silent.
            .addInterceptor(
                HttpLoggingInterceptor().setLevel(
                    if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
                    else HttpLoggingInterceptor.Level.NONE
                )
            )
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(@BaseUrl baseUrl: String, okHttpClient: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    /**
     * Font downloads ride on a dedicated Retrofit instance whose client is
     * derived from the shared one: same connection pool, dispatcher and debug
     * logging interceptor, but with the longer timeouts multi-MB font payloads
     * need. No converter factory — the body is streamed raw.
     */
    @Provides
    @Singleton
    fun provideFontDownloadApi(
        okHttpClient: OkHttpClient,
        @BaseUrl baseUrl: String,
    ): FontDownloadApi =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(
                okHttpClient.newBuilder()
                    .connectTimeout(FONT_CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .readTimeout(FONT_READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .build()
            )
            .build()
            .create(FontDownloadApi::class.java)
}

private const val FONT_CONNECT_TIMEOUT_MS = 15_000L
private const val FONT_READ_TIMEOUT_MS = 60_000L
