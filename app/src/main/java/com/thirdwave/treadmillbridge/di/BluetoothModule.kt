package com.thirdwave.treadmillbridge.di

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

/**
 * Hilt module for Bluetooth dependencies.
 * Provides BluetoothManager, BluetoothAdapter, and IO dispatcher.
 */
@Module
@InstallIn(SingletonComponent::class)
object BluetoothModule {
    
    @Provides
    @Singleton
    fun provideBluetoothManager(
        @ApplicationContext context: Context
    ): BluetoothManager? {
        return context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }
    
    @Provides
    @Singleton
    fun provideBluetoothAdapter(
        bluetoothManager: BluetoothManager?
    ): BluetoothAdapter? {
        return bluetoothManager?.adapter
    }
    
    @Provides
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
