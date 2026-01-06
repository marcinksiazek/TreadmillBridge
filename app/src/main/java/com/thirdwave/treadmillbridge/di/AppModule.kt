package com.thirdwave.treadmillbridge.di

import com.thirdwave.treadmillbridge.data.repository.TreadmillRepository
import com.thirdwave.treadmillbridge.data.repository.TreadmillRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * App-level Hilt module.
 * Provides repository interface binding.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    
    @Binds
    @Singleton
    abstract fun bindTreadmillRepository(
        impl: TreadmillRepositoryImpl
    ): TreadmillRepository
}
