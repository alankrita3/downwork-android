package com.raviga.downwork.di

import android.app.Application
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.raviga.downwork.BuildConfig
import com.raviga.downwork.data.api.AudioUploader
import com.raviga.downwork.data.api.AuthInterceptor
import com.raviga.downwork.data.api.DownWorkApi
import com.raviga.downwork.data.api.JobRunner
import com.raviga.downwork.data.api.ReRegisterAuthenticator
import com.raviga.downwork.data.audio.AudioRecorder
import com.raviga.downwork.data.audio.DictationEngine
import com.raviga.downwork.data.billing.BillingManager
import com.raviga.downwork.data.demo.DemoApi
import com.raviga.downwork.data.local.CacheStore
import com.raviga.downwork.data.local.PrefsStore
import com.raviga.downwork.data.local.SessionStore
import com.raviga.downwork.data.repo.CreditsRepository
import com.raviga.downwork.data.repo.ProjectRepository
import com.raviga.downwork.data.repo.SessionRepository
import com.raviga.downwork.push.PushTokenRegistrar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.create
import java.util.concurrent.TimeUnit

sealed interface AppEvent {
    /** A push said this project changed; screens showing it refresh. */
    data class ProjectUpdated(val projectId: String) : AppEvent
    data object CreditsUpdated : AppEvent
    data object ExportReady : AppEvent
}

/**
 * Hand-wired dependency graph: one instance per process, created by
 * [com.raviga.downwork.DownWorkApp]. Small enough that a DI framework would
 * cost more than it saves.
 */
class AppContainer(private val app: Application) {

    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        encodeDefaults = true
        isLenient = true
    }

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val sessionStore = SessionStore(app)
    val prefs = PrefsStore(app)
    val cache = CacheStore(app, json)

    /**
     * True until secrets.properties names a backend, or when a debug build has
     * the "demo backend" switch on in Settings; the demo backend then serves everything.
     */
    val isDemo: Boolean = BuildConfig.API_BASE_URL.isBlank() ||
        (BuildConfig.DEBUG && com.raviga.downwork.data.local.DebugFlags.useDemoBackend(app))

    private val baseUrl: String = BuildConfig.API_BASE_URL.let { if (it.isBlank() || it.endsWith("/")) it else "$it/" }

    /** No interceptors: used for presigned uploads and the token re-register call. */
    val plainHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .build()

    val okHttp: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor { sessionStore.accessToken })
        .authenticator(ReRegisterAuthenticator(baseUrl, plainHttp, json, sessionStore))
        .apply {
            if (BuildConfig.DEBUG) addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
        }
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val api: DownWorkApi = if (isDemo) {
        DemoApi(cache, json)
    } else {
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttp)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create()
    }

    val jobs = JobRunner(api, json)
    val uploader = AudioUploader(plainHttp)

    val session = SessionRepository(api, json, jobs, sessionStore, prefs, cache)
    val projects = ProjectRepository(api, json, jobs, uploader, cache)
    val billing = BillingManager(app, BuildConfig.REVENUECAT_API_KEY)
    val credits = CreditsRepository(api, json, cache, billing)
    val push = PushTokenRegistrar(app, prefs, session, appScope)

    val dictation = DictationEngine(app)
    val recorder = AudioRecorder(app)

    private val _events = MutableSharedFlow<AppEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<AppEvent> = _events.asSharedFlow()
    fun emit(event: AppEvent) { _events.tryEmit(event) }
}
