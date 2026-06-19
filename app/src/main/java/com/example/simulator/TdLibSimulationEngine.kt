package com.example.simulator

import android.os.Handler
import android.os.Looper
import android.util.Log
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

object TdLibSimulationEngine {
    private const val TAG = "TdLibSimulation"
    private val activeClients = ConcurrentHashMap.newKeySet<Client>()
    private val mainHandler = Handler(Looper.getMainLooper())

    // Tracks simulating authentication state per Client instance
    private val authStates = ConcurrentHashMap<Client, TdApi.AuthorizationState>()

    @JvmStatic
    fun registerClient(client: Client) {
        activeClients.add(client)
        authStates[client] = TdApi.AuthorizationStateWaitTdlibParameters()
        
        // Push initial state
        postDelayed(500) {
            client.dispatchUpdate(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateWaitTdlibParameters()))
        }
    }

    @JvmStatic
    fun unregisterClient(client: Client) {
        activeClients.remove(client)
        authStates.remove(client)
    }

    @JvmStatic
    fun handleQuery(client: Client, id: Long, query: TdApi.Function, handler: Client.ResultHandler?) {
        Log.d(TAG, "Simulating query $id: ${query.javaClass.simpleName}")
        
        when (query) {
            is TdApi.SetTdlibParameters -> {
                authStates[client] = TdApi.AuthorizationStateWaitPhoneNumber()
                postResponse(handler, TdApi.Ok())
                postDelayed(1000) {
                    client.dispatchUpdate(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateWaitPhoneNumber()))
                }
            }
            is TdApi.SetAuthenticationPhoneNumber -> {
                authStates[client] = TdApi.AuthorizationStateWaitCode()
                postResponse(handler, TdApi.Ok())
                postDelayed(1000) {
                    client.dispatchUpdate(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateWaitCode()))
                }
            }
            is TdApi.CheckAuthenticationCode -> {
                if (query.code == "0000" || query.code.length == 4 || query.code.length == 5) {
                    authStates[client] = TdApi.AuthorizationStateReady()
                    postResponse(handler, TdApi.Ok())
                    postDelayed(1000) {
                        client.dispatchUpdate(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateReady()))
                    }
                } else {
                    postResponse(handler, TdApi.Error(400, "Invalid verification code! Use 0000 or any 4 digit code."))
                }
            }
            is TdApi.CheckAuthenticationPassword -> {
                authStates[client] = TdApi.AuthorizationStateReady()
                postResponse(handler, TdApi.Ok())
                postDelayed(1000) {
                    client.dispatchUpdate(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateReady()))
                }
            }
            is TdApi.SearchPublicChat -> {
                val username = query.username.trim().removePrefix("@")
                if (username.isEmpty()) {
                    postResponse(handler, TdApi.Error(400, "Username can't be empty"))
                } else {
                    postResponse(handler, TdApi.Chat(987654321L, "TG Stream - $username Channel"))
                }
            }
            is TdApi.GetChatHistory -> {
                // Return high quality dummy TV series messages loaded with Burmese/Arabic digits
                val messages = generateMockMessages(query.chatId)
                postResponse(handler, TdApi.Messages(messages.toTypedArray()))
            }
            is TdApi.DownloadFile -> {
                simulateFileDownload(client, query.fileId, handler)
            }
            else -> {
                postResponse(handler, TdApi.Ok())
            }
        }
    }

    private fun postResponse(handler: Client.ResultHandler?, response: TdApi.Object) {
        if (handler != null) {
            mainHandler.post {
                handler.onResult(response)
            }
        }
    }

    private fun postDelayed(delayMs: Long, action: () -> Unit) {
        mainHandler.postDelayed(action, delayMs)
    }

    private fun generateMockMessages(chatId: Long): List<TdApi.Message> {
        val list = mutableListOf<TdApi.Message>()
        
        // Let's pack various Series captures to test the Phase 3 Scraper Parse rules:
        val captions = listOf(
            // --- Series 1: Squid Game (Burmese + Arabic mix) ---
            "Squid Game\nအပိုင်း (၁) - ရေခဲမုန့်\nအရည်အသွေး: 1080p\nမင်းသားများစိန်ခေါ်မှု",
            "Squid Game\nအပိုင်း (၂) - ငရဲပြည်\nအရည်အသွေး: 720p\nမင်းသားများစိန်ခေါ်မှု",
            "Squid Game အပိုင်း (၃) ဖြစ်ရပ်မှန် 480p\nဂိမ်းတွင်းဇာတ်လမ်း",
            "အပိုင်း(၄) Squid Game - 1080p\nဆက်လက်ကြည့်ရှုပါ",
            "Squid Game • Ep.5 [720p]\nအကွေ့အကောက်များ",

            // --- Series 2: Stranger Things (Arabic style) ---
            "Stranger Things • Episode 1 (1080p)\nအမှောင်ကမ္ဘာထဲဆီသို့",
            "Stranger Things • EP.2 [720p]\nအခြားကမ္ဘာ",
            "Stranger Things အပိုင်း (၃) - 480p\nအစိုးရဓာတ်ခွဲခန်း",
            "Stranger Things - Ep.4 - 1080p\nဆယ်ကျော်သက်များရဲ့စွန့်စားခန်း",

            // --- Series 3: Breaking Bad ---
            "Breaking Bad - Ep.01 • 1080p\nဓာတုဗေဒဆရာနှင့် ကျောင်းသားဟောင်း",
            "Breaking Bad - Episode 02 • 720p\nပြဿနာအစ ဆရာဘဝ",
            "Breaking Bad - Ep.3 [480p]\nမူးယစ်ဆေးလောက"
        )

        var messageId = 20000L
        var fileIdCounter = 1000

        for (caption in captions) {
            val fileId = fileIdCounter++
            // TDLib structure: Message -> MessageVideo -> Video -> File -> LocalFile
            val localFile = TdApi.LocalFile(
                "/data/user/0/com.example/cache/simulated_file_$fileId.mp4",
                false,
                false,
                0L
            )
            val fileObj = TdApi.File(fileId, 250_000_000L, 250_000_000L, localFile)
            val videoObj = TdApi.Video("video_$fileId.mp4", 3100, 1920, 1080, fileObj)
            val messageVideo = TdApi.MessageVideo(videoObj, TdApi.FormattedText(caption))
            
            list.add(TdApi.Message(messageId++, chatId, messageVideo))
        }

        return list
    }

    private fun simulateFileDownload(client: Client, fileId: Int, handler: Client.ResultHandler?) {
        val totalSize = 250_000_000L // 250MB
        var downloaded = 5_000_000L  // Start with 5MB cached
        
        val localFile = TdApi.LocalFile(
            "/data/user/0/com.example/cache/simulated_file_$fileId.mp4",
            true,
            false,
            downloaded
        )
        val fileObj = TdApi.File(fileId, totalSize, totalSize, localFile)
        
        // Immediately return the initial download action success
        postResponse(handler, fileObj)
        
        // Periodically push UpdateFile updates simulating download progress
        thread {
            try {
                // Simulate progressive downloads inside background thread
                while (downloaded < totalSize) {
                    Thread.sleep(500)
                    downloaded += 25_000_000L // Step up by 25MB
                    if (downloaded >= totalSize) downloaded = totalSize
                    
                    val runningLocal = TdApi.LocalFile(
                        "/data/user/0/com.example/cache/simulated_file_$fileId.mp4",
                        downloaded < totalSize,
                        downloaded >= totalSize,
                        downloaded
                    )
                    val updatedFile = TdApi.File(fileId, totalSize, totalSize, runningLocal)
                    
                    client.dispatchUpdate(TdApi.UpdateFile(updatedFile))
                }
            } catch (e: InterruptedException) {
                // Cancelled
            }
        }
    }
}
