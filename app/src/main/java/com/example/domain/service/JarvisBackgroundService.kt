package com.example.domain.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.api.GeminiClient
import com.example.data.api.WeatherClient
import com.example.data.db.JarvisDatabase
import com.example.data.model.ChatMessage
import com.example.data.model.MessageSender
import com.example.data.repository.JarvisRepository
import com.example.domain.ai.JarvisBrain
import com.example.domain.security.SecurityEngine
import com.example.domain.tools.WeatherTool
import com.example.domain.voice.VoiceEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class JarvisBackgroundService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private var wakeLock: PowerManager.WakeLock? = null
    private var voiceEngine: VoiceEngine? = null
    private var brain: JarvisBrain? = null
    private var repository: JarvisRepository? = null

    companion object {
        const val TAG = "JarvisSentinelService"
        const val CHANNEL_ID = "jarvis_sentinel_channel"
        const val NOTIFICATION_ID = 1007

        const val ACTION_START = "com.example.action.START_SENTINEL"
        const val ACTION_STOP = "com.example.action.STOP_SENTINEL"

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        private val _lastStatusText = MutableStateFlow("STANDBY")
        val lastStatusText: StateFlow<String> = _lastStatusText.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, JarvisBackgroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, JarvisBackgroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "JarvisBackgroundService created.")
        createNotificationChannel()

        // Acquire partial wake lock to keep speech detector active during screen off
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "JARVIS::SentinelAudioLock").apply {
            setReferenceCounted(false)
            acquire(24 * 60 * 60 * 1000L) // 24 hours max
        }

        // Initialize background data & AI brain
        val database = JarvisDatabase.getInstance(applicationContext)
        val repo = JarvisRepository(database)
        repository = repo
        val securityEngine = SecurityEngine(repo)
        val geminiClient = GeminiClient()
        val weatherClient = WeatherClient()
        val weatherTool = WeatherTool(weatherClient)

        brain = JarvisBrain(
            context = applicationContext,
            repository = repo,
            geminiClient = geminiClient,
            securityEngine = securityEngine,
            weatherTool = weatherTool
        )

        // Initialize background VoiceEngine in continuous listening mode
        voiceEngine = VoiceEngine(
            context = applicationContext,
            scope = serviceScope,
            onSpeechRecognized = { command ->
                handleCommandFromBackground(command)
            },
            onWakeWordDetected = { remainder ->
                handleWakeWordActivated(remainder)
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Log.i(TAG, "Stopping Jarvis Sentinel Service requested.")
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundNotification("Listening for 'Wake up' or 'Jarvis'...")
        _isServiceRunning.value = true
        _lastStatusText.value = "SENTINEL ACTIVE // MONITORING 'WAKE UP'"

        voiceEngine?.setContinuousListening(true)

        return START_STICKY
    }

    private fun handleWakeWordActivated(remainder: String) {
        Log.i(TAG, "Wake word detected in background! Remainder: $remainder")
        _lastStatusText.value = "WAKE WORD DETECTED // ACTIVATING"
        updateNotification("Wake up detected: 'At your service, sir'")

        // Wake screen when locked
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            val screenLock = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "JARVIS::LockScreenWake"
            )
            screenLock.acquire(8000L)
            val launchIntent = Intent(applicationContext, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
            startActivity(launchIntent)
        } catch (e: Exception) {
            Log.w(TAG, "Could not wake lock screen", e)
        }

        if (remainder.isBlank()) {
            voiceEngine?.speak("At your service, sir.") {
                _lastStatusText.value = "SENTINEL ACTIVE // LISTENING"
                updateNotification("Listening for instruction...")
                voiceEngine?.startListeningForCommand()
            }
        } else {
            handleCommandFromBackground(remainder)
        }
    }

    private fun handleCommandFromBackground(command: String) {
        if (command.isBlank()) return
        Log.i(TAG, "Processing background directive: $command")
        _lastStatusText.value = "PROCESSING: $command"
        updateNotification("Processing: \"$command\"")

        serviceScope.launch {
            try {
                // Record user message in audit log
                repository?.logAction(
                    action = "BACKGROUND_WAKE_WORD_DIRECTIVE",
                    details = "Input: $command",
                    status = "PROCESSING"
                )

                val response = brain?.processUserQuery(
                    userInput = command,
                    onRequestConfirmation = { req ->
                        // If dangerous action, notify user to open app
                        voiceEngine?.speak("Confirmation required for this operation, sir. Please authorize in the core HUD.")
                        updateNotification("Confirmation needed: ${req.title}")
                    }
                )

                val responseText = response?.text ?: "Directive executed, sir."
                _lastStatusText.value = "RESPONSE: ${responseText.take(40)}"
                updateNotification("JARVIS: ${responseText.take(60)}")

                // Speak response out loud
                voiceEngine?.speak(responseText) {
                    _lastStatusText.value = "SENTINEL ACTIVE // MONITORING 'WAKE UP'"
                    updateNotification("Listening for 'Wake up' in background...")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error executing background query", e)
                voiceEngine?.speak("I encountered an anomaly processing that directive, sir.")
                _lastStatusText.value = "SENTINEL ACTIVE // ANOMALY"
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "J.A.R.V.I.S. Background Sentinel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Continuous background listening and wake-word detection for J.A.R.V.I.S."
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundNotification(status: String) {
        val notification = buildNotification(status)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(status: String) {
        val notification = buildNotification(status)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(statusText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, JarvisBackgroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("J.A.R.V.I.S. // SENTINEL ACTIVE")
            .setContentText(statusText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(R.mipmap.ic_launcher, "OPEN HUD", openPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "DISENGAGE", stopPendingIntent)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "JarvisBackgroundService destroyed.")
        _isServiceRunning.value = false
        _lastStatusText.value = "OFFLINE"

        voiceEngine?.destroy()
        voiceEngine = null

        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing wake lock", e)
        }

        serviceScope.cancel()
    }
}
