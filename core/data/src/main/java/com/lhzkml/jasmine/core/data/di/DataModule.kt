package com.lhzkml.jasmine.core.data.di

import com.lhzkml.jasmine.core.data.datastore.UserPreferencesDataStore
import com.lhzkml.jasmine.core.data.manager.dispatcher.DispatcherManager
import com.lhzkml.jasmine.core.data.manager.dispatcher.DispatcherManagerImpl
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
}
