package com.lhzkml.jasmine.core.data.di

import com.lhzkml.jasmine.core.data.model.BuiltInProviders
import com.lhzkml.jasmine.core.data.model.ModelList
import com.lhzkml.jasmine.core.data.datastore.ProviderDataStore
import com.lhzkml.jasmine.core.data.datastore.UserPreferencesDataStore
import com.lhzkml.jasmine.core.data.manager.dispatcher.DispatcherManager
import com.lhzkml.jasmine.core.data.manager.dispatcher.DispatcherManagerImpl
import com.lhzkml.jasmine.core.data.repository.AppLanguageRepository
import com.lhzkml.jasmine.core.data.repository.AppLanguageRepositoryImpl
import com.lhzkml.jasmine.core.data.repository.ProviderRepository
import com.lhzkml.jasmine.core.data.repository.ProviderRepositoryImpl
import com.lhzkml.jasmine.core.data.repository.UserPreferencesRepository
import com.lhzkml.jasmine.core.data.repository.UserPreferencesRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDispatcherManager(): DispatcherManager = DispatcherManagerImpl()

    @Provides
    @Singleton
    fun provideUserPreferencesRepository(
        userPreferencesDataStore: UserPreferencesDataStore,
        dispatcherManager: DispatcherManager,
    ): UserPreferencesRepository = UserPreferencesRepositoryImpl(
        userPreferencesDataStore = userPreferencesDataStore,
        dispatcherManager = dispatcherManager,
    )

    @Provides
    @Singleton
    fun provideAppLanguageRepository(): AppLanguageRepository = AppLanguageRepositoryImpl()

    @Provides
    @Singleton
    fun provideProviderRepository(
        providerDataStore: ProviderDataStore,
        modelList: ModelList,
        builtInProviders: BuiltInProviders,
        dispatcherManager: DispatcherManager,
    ): ProviderRepository = ProviderRepositoryImpl(
        providerDataStore = providerDataStore,
        modelList = modelList,
        builtInProviders = builtInProviders,
        dispatcherManager = dispatcherManager,
    )
}
