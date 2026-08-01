package com.jumastappworks.mapstead.di

import com.jumastappworks.mapstead.data.repository.*
import com.jumastappworks.mapstead.data.mapping.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindPropertyRepository(
        propertyRepositoryImpl: PropertyRepositoryImpl
    ): PropertyRepository

    @Binds
    @Singleton
    abstract fun bindInfrastructureRepository(
        infrastructureRepositoryImpl: InfrastructureRepositoryImpl
    ): InfrastructureRepository

    @Binds
    @Singleton
    abstract fun bindCurrentLocationProvider(
        locationTracker: LocationTracker
    ): CurrentLocationProvider

    @Binds
    @Singleton
    abstract fun bindBasemapProvider(
        impl: ProductionBasemapProvider
    ): BasemapProvider

    @Binds
    @Singleton
    abstract fun bindAddressLocationResolver(
        impl: ProductionAddressLocationResolver
    ): AddressLocationResolver
}
