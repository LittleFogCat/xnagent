package tech.xiaoniu.xnagent

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.room.Room
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
import tech.xiaoniu.xnagent.data.repository.AuthRepository
import tech.xiaoniu.xnagent.data.repository.AuthRepositoryImpl
import tech.xiaoniu.xnagent.data.repository.FavoriteRepository
import tech.xiaoniu.xnagent.data.repository.FavoriteRepositoryImpl
import tech.xiaoniu.xnagent.data.local.AuthStore
import tech.xiaoniu.xnagent.data.local.TokenRefreshHandler
import tech.xiaoniu.xnagent.data.local.XNDatabase
import tech.xiaoniu.xnagent.data.local.dao.ChatDao
import tech.xiaoniu.xnagent.data.local.network.HttpStreamingLoggingInterceptor
import tech.xiaoniu.xnagent.data.local.network.NetworkConfig
import tech.xiaoniu.xnagent.data.remote.api.AuthApi
import tech.xiaoniu.xnagent.data.remote.api.ChatApi
import tech.xiaoniu.xnagent.data.remote.api.StreamChatApi
import tech.xiaoniu.xnagent.data.repository.HomeRepository
import tech.xiaoniu.xnagent.data.repository.HomeRepositoryImpl
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/**
 * 应用入口。
 *
 * 负责初始化 Hilt，并在启动时打开协程调试信息，便于开发阶段排查异步问题。
 */
@HiltAndroidApp
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        System.setProperty("kotlinx.coroutines.debug", "on")
    }
}

/** 应用级配置，目前主要用于区分是否开启网络日志。 */
data class AppConfig(
    val isDebug: Boolean
)

@Module
@InstallIn(SingletonComponent::class)
class AppModule {
    @Provides
    @Singleton
    fun provideFavoriteRepository(
        @ApplicationContext context: Context,
        json: Json,
    ): FavoriteRepository = FavoriteRepositoryImpl(context, json)

    @Provides
    @Singleton
    fun provideTokenRefreshHandler(
        authStore: AuthStore,
        json: Json,
    ): TokenRefreshHandler = TokenRefreshHandler(authStore, json)

    @Provides
    @Singleton
    fun provideAuthRepository(
        authApi: AuthApi,
        authStore: AuthStore,
        tokenRefreshHandler: TokenRefreshHandler,
    ): AuthRepository = AuthRepositoryImpl(authApi, authStore, tokenRefreshHandler)

    @Provides
    @Singleton
    fun provideHomeRepository(
        @ApplicationContext context: Context,
        json: Json,
        streamChatApi: StreamChatApi,
        chatApi: ChatApi,
        chatDao: ChatDao,
    ): HomeRepository = HomeRepositoryImpl(context, json, streamChatApi, chatApi, chatDao)

    /** 统一的 JSON 序列化配置，允许忽略服务端新增字段。 */
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
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

    /**
     * 提供常规 HTTP 客户端。
     *
     * 自动附带 Bearer Token、401 自动刷新 token、debug 模式日志。
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(
        appConfig: AppConfig,
        authStore: AuthStore,
        tokenRefreshHandler: TokenRefreshHandler,
    ): OkHttpClient = OkHttpClient.Builder()
        // 鉴权头注入
        .addInterceptor { chain ->
            val token = authStore.currentToken()
            val request = if (token.isNullOrBlank()) {
                chain.request()
            } else {
                chain.request().newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            }
            chain.proceed(request)
        }
        // 401 自动刷新 token
        .addInterceptor { chain ->
            val request = chain.request()
            val requestPath = request.url.encodedPath
            val originalResponse = chain.proceed(request)

            if (originalResponse.code == 401
                && requestPath != "/api/refresh"
                && requestPath != "/api/logout-v2"
            ) {
                originalResponse.close()
                val refreshed = tokenRefreshHandler.refreshToken()
                if (refreshed) {
                    val newToken = authStore.currentToken()
                    if (newToken != null) {
                        val retryRequest = request.newBuilder()
                            .header("Authorization", "Bearer $newToken")
                            .build()
                        return@addInterceptor chain.proceed(retryRequest)
                    }
                }
                // 刷新失败，清除登录态触发重新登录
                authStore.clear()
            }
            originalResponse
        }
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

    /**
     * 提供 SSE 专用 Retrofit。
     *
     * 不能复用普通客户端，因为标准 BODY 日志会一次性读取完整响应体，破坏流式消费。
     */
    @Provides
    @Singleton
    @OptIn(ExperimentalSerializationApi::class)
    @Named("sse")
    fun provideSSERetrofit(
        json: Json,
        authStore: AuthStore,
    ): retrofit2.Retrofit {
        // 不能复用 provideOkHttpClient，其 HttpLoggingInterceptor(Level.BODY)
        // 会 source.request(Long.MAX_VALUE) 把整个响应体缓冲到内存，破坏 SSE 流式读取
        val sseClient = OkHttpClient.Builder()
            .connectTimeout(0, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val token = authStore.currentToken()
                val request = if (token.isNullOrBlank()) {
                    chain.request()
                } else {
                    chain.request().newBuilder()
                        .header("Authorization", "Bearer $token")
                        .build()
                }
                chain.proceed(request)
            }
            .addInterceptor(HttpStreamingLoggingInterceptor().apply {
                level = HttpStreamingLoggingInterceptor.Level.BODY
            })
            .build()
        return retrofit2.Retrofit.Builder()
            .baseUrl(NetworkConfig.BASE_URL)
            .client(sseClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }
}

@Module
@InstallIn(SingletonComponent::class)
class DataModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): XNDatabase {
        return Room.databaseBuilder(context, XNDatabase::class.java, "xnagent.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    @Provides
    @Singleton
    fun provideChatDao(database: XNDatabase): ChatDao = database.chatDao()

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: retrofit2.Retrofit): AuthApi =
        retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideChatApi(retrofit: retrofit2.Retrofit): ChatApi =
        retrofit.create(ChatApi::class.java)

    @Provides
    @Singleton
    fun provideStreamChatApi(@Named("sse") retrofit: retrofit2.Retrofit): StreamChatApi =
        retrofit.create(StreamChatApi::class.java)
}