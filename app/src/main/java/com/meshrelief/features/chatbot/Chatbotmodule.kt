package com.meshrelief.features.chatbot

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ChatbotModule {

    @Provides
    @Singleton
    fun provideLlmEngine(): LlmEngine = OfflineKeywordEngine()

    // To enable MediaPipe later, swap to:
    // @Provides @Singleton
    // fun provideLlmEngine(@ApplicationContext ctx: Context): LlmEngine =
    //     LlmEngineWithFallback(MediaPipeLlmEngine(ctx))
}