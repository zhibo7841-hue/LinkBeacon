package com.networktoolbox.di

import android.content.Context
import com.networktoolbox.core.network.rdap.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RdapModule {
    @Provides @Singleton fun normalizer(): RdapDomainNormalizer = AndroidRdapDomainNormalizer()
    @Provides @Singleton fun lookup(@ApplicationContext context: Context): RunRdapLookup = androidRdapLookup(context)
    @Provides @Singleton fun input(@ApplicationContext context: Context): RegistrationInputResolver = androidRegistrationInput(context)
    @Provides @Singleton fun registration(@ApplicationContext context: Context,rdap:RunRdapLookup): RunRegistrationLookup =
        RegistrationLookup(rdap,AndroidRdapTransport(context))
}
