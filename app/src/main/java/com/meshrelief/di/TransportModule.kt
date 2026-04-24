package com.meshrelief.di

import com.meshrelief.mesh.wifi.MeshTransport
import com.meshrelief.mesh.wifi.WifiDirectManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TransportModule {

    @Binds
    @Singleton
    abstract fun bindMeshTransport(
        impl: WifiDirectManager
    ): MeshTransport
}