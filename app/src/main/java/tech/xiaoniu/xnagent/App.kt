package tech.xiaoniu.xnagent

import android.app.Application
import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import tech.xiaoniu.xnagent.ui.screen.home.HomeRepository
import tech.xiaoniu.xnagent.ui.screen.home.HomeRepositoryImpl
import javax.inject.Singleton

/**
 * @author littlefogcat
 * @email littlefogcat@foxmail.com
 */
@HiltAndroidApp
class App : Application() {
}

@Module
@InstallIn(SingletonComponent::class)
class AppModule {

    @Provides
    @Singleton
    fun provideHomeRepository(
        @ApplicationContext context: Context
    ): HomeRepository = HomeRepositoryImpl(context)

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
    }
}