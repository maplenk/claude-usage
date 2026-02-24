package com.qbapps.claudeusage.di

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.qbapps.claudeusage.BuildConfig
import com.qbapps.claudeusage.data.remote.AuthInterceptor
import com.qbapps.claudeusage.data.remote.ClaudeApiService
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

        // Certificate pinning for claude.ai
        builder.certificatePinner(
            CertificatePinner.Builder()
                .add("claude.ai", "sha256/leWIRt5k+j34MAUDnCEED3hpf0m2VL02ICoidBGHSAQ=") // leaf
                .add("claude.ai", "sha256/y7xVm0TVJNahMr2sZydE2jQH8SquXV9yLF9seROHHHU=") // intermediate
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
}
