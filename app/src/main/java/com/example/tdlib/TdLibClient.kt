package com.example.tdlib

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class TdLibClient private constructor(private val context: Context) {
    private var client: Client? = null
    
    // Authorization State updates
    private val _authState = MutableStateFlow<TdApi.AuthorizationState>(TdApi.AuthorizationStateClosed())
    val authState: StateFlow<TdApi.AuthorizationState> = _authState.asStateFlow()

    // File download updates (maps fileId -> TdApi.File)
    private val _fileUpdates = MutableStateFlow<Map<Int, TdApi.File>>(emptyMap())
    val fileUpdates: StateFlow<Map<Int, TdApi.File>> = _fileUpdates.asStateFlow()

    companion object {
        private const val TAG = "TdLibClientWrapper"
        @Volatile
        private var INSTANCE: TdLibClient? = null

        fun getInstance(context: Context): TdLibClient {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TdLibClient(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    init {
        setupClient()
    }

    private fun setupClient() {
        Log.i(TAG, "Initializing inner TdLib Client instance")
        client = Client.create(
            { result -> handleUpdate(result) },
            { exc -> Log.e(TAG, "Received client background exception", exc) },
            { exc -> Log.e(TAG, "Received client default exception", exc) }
        )
    }

    private fun handleUpdate(obj: TdApi.Object) {
        when (obj) {
            is TdApi.UpdateAuthorizationState -> {
                Log.d(TAG, "Updated Authorization State: ${obj.authorizationState}")
                _authState.value = obj.authorizationState
            }
            is TdApi.UpdateFile -> {
                val file = obj.file
                val current = _fileUpdates.value.toMutableMap()
                current[file.id] = file
                _fileUpdates.value = current
            }
        }
    }

    suspend fun send(query: TdApi.Function): TdApi.Object = suspendCancellableCoroutine { continuation ->
        val innerClient = client
        if (innerClient == null) {
            if (continuation.isActive) {
                continuation.resumeWithException(IllegalStateException("TDLib Client is not initialized"))
            }
            return@suspendCancellableCoroutine
        }

        innerClient.send(query) { result ->
            if (continuation.isActive) {
                if (result is TdApi.Error) {
                    continuation.resumeWithException(Exception("TDLib Error ${result.code}: ${result.message}"))
                } else {
                    continuation.resume(result)
                }
            }
        }
    }

    fun destroy() {
        client?.destroy()
        client = null
    }
}
