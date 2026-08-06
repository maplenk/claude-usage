package com.qbapps.claudeusage.di

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.qbapps.claudeusage.BuildConfig
import com.qbapps.claudeusage.data.remote.AuthInterceptor
import com.qbapps.claudeusage.data.remote.ClaudeApiService
import com.qbapps.claudeusage.data.remote.CodexApiContract
import com.qbapps.claudeusage.data.remote.CodexAuthApiService
import com.qbapps.claudeusage.data.remote.CodexUsageApiService
import com.qbapps.claudeusage.data.remote.GrokApiContract
import com.qbapps.claudeusage.data.remote.GrokApiService
import com.qbapps.claudeusage.data.remote.UtilizationAdapter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder()
        .registerTypeAdapter(Double::class.java, UtilizationAdapter())
        .create()

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)

        // Certificate pinning for claude.ai — pinned to Let's Encrypt root CAs
        // (leaf/intermediate certs rotate every ~3 months; roots are stable for years)
        builder.certificatePinner(
            CertificatePinner.Builder()
                .add("claude.ai", "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M=") // ISRG Root X1 (expires 2035)
                .add("claude.ai", "sha256/diGVwiVYbubAI3RW4hB9xU8e/CH2GnkuvVFZE8zmgzI=") // ISRG Root X2 (expires 2040)
                .build()
        )

        // Only add HTTP logging in debug builds, with credential redaction
        if (BuildConfig.DEBUG) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
                redactHeader("Cookie")
                redactHeader("Authorization")
            }
            builder.addInterceptor(logging)
        }

        return builder.build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, gson: Gson): Retrofit = Retrofit.Builder()
        .baseUrl(ClaudeApiService.BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    @Provides
    @Singleton
    fun provideClaudeApiService(retrofit: Retrofit): ClaudeApiService =
        retrofit.create(ClaudeApiService::class.java)

    @Provides
    @Singleton
    @CodexHttpClient
    fun provideCodexHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)

        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                    redactHeader("Authorization")
                    redactHeader("ChatGPT-Account-Id")
                }
            )
        }
        return builder.build()
    }

    @Provides
    @Singleton
    fun provideCodexAuthApiService(
        @CodexHttpClient client: OkHttpClient,
        gson: Gson,
    ): CodexAuthApiService = Retrofit.Builder()
        .baseUrl(CodexApiContract.AUTH_BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
        .create(CodexAuthApiService::class.java)

    @Provides
    @Singleton
    fun provideCodexUsageApiService(
        @CodexHttpClient client: OkHttpClient,
        gson: Gson,
    ): CodexUsageApiService = Retrofit.Builder()
        .baseUrl(CodexApiContract.USAGE_BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
        .create(CodexUsageApiService::class.java)

    @Provides
    @Singleton
    @GrokHttpClient
    fun provideGrokHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                    redactHeader("Authorization")
                }
            )
        }
        return builder.build()
    }

    @Provides
    @Singleton
    fun provideGrokApiService(
        @GrokHttpClient client: OkHttpClient,
        gson: Gson,
    ): GrokApiService = Retrofit.Builder()
        .baseUrl(GrokApiContract.BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
        .create(GrokApiService::class.java)
}
