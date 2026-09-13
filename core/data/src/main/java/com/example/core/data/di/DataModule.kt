package com.example.core.data.di

import com.example.core.data.datastore.UserPreferencesDataStore
import com.example.core.data.manager.dispatcher.DispatcherManager
import com.example.core.data.manager.dispatcher.DispatcherManagerImpl
import com.example.core.data.repository.UserPreferencesRepository
import com.example.core.data.repository.UserPreferencesRepositoryImpl
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
