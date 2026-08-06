package com.qbapps.claudeusage.di

import com.qbapps.claudeusage.data.repository.UsageRepositoryImpl
import com.qbapps.claudeusage.data.repository.CodexUsageRepositoryImpl
import com.qbapps.claudeusage.domain.repository.CodexUsageRepository
import com.qbapps.claudeusage.domain.repository.UsageRepository
import com.qbapps.claudeusage.data.repository.GrokUsageRepositoryImpl
import com.qbapps.claudeusage.domain.repository.GrokUsageRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindUsageRepository(impl: UsageRepositoryImpl): UsageRepository

    @Binds
    @Singleton
    abstract fun bindCodexUsageRepository(
        impl: CodexUsageRepositoryImpl,
    ): CodexUsageRepository

    @Binds
    @Singleton
    abstract fun bindGrokUsageRepository(
        impl: GrokUsageRepositoryImpl,
    ): GrokUsageRepository
}
