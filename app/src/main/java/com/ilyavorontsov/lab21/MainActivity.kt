package com.ilyavorontsov.lab21

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.*
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer

class TimerService : Service() {
    private val binder = LocalBinder()
    val minutesLeft = MutableLiveData<Int>()
    val secondsLeft = MutableLiveData<Int>()
    val isRunning = MutableLiveData<Boolean>()
    private lateinit var timer: CountDownTimer
    val CHANNEL_MSG_ID = "lab21_channel_msg"
    private lateinit var notificationManager: NotificationManager

    companion object {
        private var instance: TimerService? = null
        fun getInstance(): TimerService? = instance
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        val channel = NotificationChannel(
            CHANNEL_MSG_ID, "Таймер", NotificationManager.IMPORTANCE_DEFAULT
        )
        channel.description = "Обратный отсчет"
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    private fun publicNotification() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_MSG_ID)
            .setSmallIcon(R.drawable.timer_48px)
            .setContentTitle("Таймер")
            .setContentText(
                String.format(
                    getString(R.string.timer),
                    minutesLeft.value.toString(),
                    secondsLeft.value.toString()
                )
            )
            .setContentIntent(pendingIntent)
        val notification = builder.build()
        startForeground(1, notification)
    }

    private fun removeNotification() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    @SuppressLint("MissingPermission")
    fun startTimer(minutes: Int, seconds: Int) {
        minutesLeft.value = minutes
        secondsLeft.value = seconds
        isRunning.value = true

        timer = object : CountDownTimer((minutes * 60_000 + seconds * 1_000).toLong(), 1_000) {
            override fun onTick(millisUntilFinished: Long) {
                Log.d("TICK", "${minutesLeft.value}:${secondsLeft.value}")
                if (secondsLeft.value == 0) {
                    secondsLeft.value = 59
                    minutesLeft.value = minutesLeft.value!! - 1
                } else {
                    secondsLeft.value = secondsLeft.value!! - 1
                }
                publicNotification()
            }

            override fun onFinish() {
                isRunning.value = false
            }
        }
        timer.start()

    }

    fun stopTimer() {
        isRunning.value = false
        removeNotification()
        timer.cancel()
    }

    inner class LocalBinder : Binder() {
        fun getService(): TimerService = this@TimerService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }
}

class MainActivity : AppCompatActivity() {

    private val timerService = MutableLiveData<TimerService>()
    private var isBound = false
    private lateinit var btnTimer: Button

    private lateinit var etMinutes: EditText
    private lateinit var etSeconds: EditText

    private lateinit var tvTimerStatus: TextView

    private val requestResult = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startTimer()
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as TimerService.LocalBinder
            timerService.value = binder.getService()
            isBound = true

            timerService.value?.minutesLeft?.observe(this@MainActivity) { minutesLeft ->
                tvTimerStatus.text =
                    String.format(getString(R.string.timer), minutesLeft.toString(), timerService.value?.secondsLeft?.value.toString())
            }
            timerService.value?.secondsLeft?.observe(this@MainActivity) { secondsLeft ->
                tvTimerStatus.text =
                    String.format(getString(R.string.timer), timerService.value?.minutesLeft?.value.toString(), secondsLeft.toString())
            }
            timerService.value?.isRunning?.observe(this@MainActivity) { isRunning ->
                if (isRunning) {
                    etMinutes.isEnabled = false
                    etSeconds.isEnabled = false
                    btnTimer.text = getString(R.string.stop)
                    btnTimer.setOnClickListener {
                        if (isBound && timerService.value != null) {
                            timerService.value!!.stopTimer()
                        }
                    }
                }
                if (!isRunning) {
                    etMinutes.isEnabled = true
                    etSeconds.isEnabled = true
                    btnTimer.text = getString(R.string.start)
                    tvTimerStatus.text = getString(R.string.timer_ready)
                    btnTimer.setOnClickListener {
                        if (isBound && timerService.value != null) {
                            timerService.value!!.startTimer(
                                Integer.parseInt(etMinutes.text.toString()),
                                Integer.parseInt(etSeconds.text.toString())
                            )
                        }
                    }
                }
            }
        }

        override fun onServiceDisconnected(className: ComponentName) {
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        tvTimerStatus = findViewById(R.id.tvTimerStatus)

        if (TimerService.getInstance() != null) {
            val intent = Intent(this, TimerService::class.java)
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        } else {
            val intent = Intent(this, TimerService::class.java)
            startService(intent)
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }

        etMinutes = findViewById(R.id.etMinutes)
        etSeconds = findViewById(R.id.etSeconds)

        btnTimer = findViewById(R.id.btnTimer)
        btnTimer.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13 и выше
                if (ActivityCompat.checkSelfPermission(
                        this, android.Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestResult.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    startTimer()
                }
            } else {
                startTimer()
            }
        }
    }

    private fun startTimer() {
        if (isBound && timerService.value != null) {
            timerService.value!!.startTimer(
                Integer.parseInt(etMinutes.text.toString()),
                Integer.parseInt(etSeconds.text.toString())
            )
        }
    }
}