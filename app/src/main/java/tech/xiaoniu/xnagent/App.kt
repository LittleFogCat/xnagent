package tech.xiaoniu.xnagent

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import tech.xiaoniu.xnagent.data.local.network.NetworkConfig
import tech.xiaoniu.xnagent.data.remote.api.ChatApi
import tech.xiaoniu.xnagent.data.remote.api.StreamChatApi
import tech.xiaoniu.xnagent.ui.screen.home.HomeRepository
import tech.xiaoniu.xnagent.ui.screen.home.HomeRepositoryImpl
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/**
 * @author littlefogcat
 * @email littlefogcat@foxmail.com
 */
@HiltAndroidApp
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        System.setProperty("kotlinx.coroutines.debug", "on")
    }
}

data class AppConfig(
    val isDebug: Boolean
)

@Module
@InstallIn(SingletonComponent::class)
class AppModule {

    @Provides
    @Singleton
    fun provideHomeRepository(
        @ApplicationContext context: Context,
        json: Json,
        streamChatApi: StreamChatApi,
        chatApi: ChatApi
    ): HomeRepository = HomeRepositoryImpl(context, json, streamChatApi, chatApi)

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
    }

    @Provides
    @Singleton
    fun provideAppConfig(@ApplicationContext context: Context) = AppConfig(
        isDebug = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    )
}

@Module
@InstallIn(SingletonComponent::class)
class NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(
        appConfig: AppConfig
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = if (appConfig.isDebug) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        })
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    @OptIn(ExperimentalSerializationApi::class)
    fun provideNormalRetrofit(
        json: Json,
        okhttp: OkHttpClient
    ): retrofit2.Retrofit {
        return retrofit2.Retrofit.Builder()
            .baseUrl(NetworkConfig.BASE_URL)
            .client(okhttp)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    @OptIn(ExperimentalSerializationApi::class)
    @Named("sse")
    fun provideSSERetrofit(
        json: Json,
        okhttp: OkHttpClient
    ): retrofit2.Retrofit {
        return retrofit2.Retrofit.Builder()
            .baseUrl(NetworkConfig.BASE_URL)
            .client(
                okhttp.newBuilder()
                    .connectTimeout(0, TimeUnit.SECONDS)
                    .readTimeout(0, TimeUnit.SECONDS)
                    .build()
            )
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideChatApi(retrofit: retrofit2.Retrofit): ChatApi =
        retrofit.create(ChatApi::class.java)

    @Provides
    @Singleton
    fun provideStreamChatApi(@Named("sse") retrofit: retrofit2.Retrofit): StreamChatApi =
        retrofit.create(StreamChatApi::class.java)

}