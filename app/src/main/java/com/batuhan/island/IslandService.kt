package com.batuhan.island

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.telephony.SignalStrength
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.view.Gravity
import android.view.WindowManager

class IslandService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var ring: RingView
    private lateinit var lp: WindowManager.LayoutParams
    private lateinit var prefs: SharedPreferences
    private val h = Handler(Looper.getMainLooper())
    private var ready = false
    private var pct = 0
    private var charging = false
    private var sig = 0
    private var telCb: TelephonyCallback? = null

    private val battRx = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
            val s = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            pct = l * 100 / s
            val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            charging = st == BatteryManager.BATTERY_STATUS_CHARGING ||
                st == BatteryManager.BATTERY_STATUS_FULL
            render()
        }
    }
    private val prefL = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        applyLayout()
        if (key == "gizle") {
            if (prefs.getBoolean("gizle", false)) DurumCubugu.uygula(this)
            else DurumCubugu.geriGetir(this)
        }
    }
    private val tick = object : Runnable {
        override fun run() { render(); h.postDelayed(this, 2000) }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate() {
        super.onCreate()
        startFg()
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
        prefs = getSharedPreferences("island", MODE_PRIVATE)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        ring = RingView(this)

        lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        fillLp()
        wm.addView(ring, lp)
        ready = true

        registerReceiver(battRx, IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_NOT_EXPORTED)
        prefs.registerOnSharedPreferenceChangeListener(prefL)
        if (prefs.getBoolean("gizle", false)) DurumCubugu.uygula(this)
        try {
            val tm = getSystemService(TelephonyManager::class.java)
            sig = tm.signalStrength?.level ?: 0
            val cb = object : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener {
                override fun onSignalStrengthsChanged(s: SignalStrength) { sig = s.level; render() }
            }
            tm.registerTelephonyCallback(mainExecutor, cb)
            telCb = cb
        } catch (e: SecurityException) { /* telefon izni verilmemiş */ }
        h.post(tick)
    }

    private fun startFg() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("island", "Island", NotificationManager.IMPORTANCE_MIN))
        val n = Notification.Builder(this, "island")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Island çalışıyor")
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else startForeground(1, n)
    }

    private fun fillLp() {
        val size = dp(prefs.getInt("size", 32).coerceAtLeast(16))
        lp.width = size
        lp.height = size
        lp.x = dp(prefs.getInt("x", 230) - 200)
        lp.y = dp(prefs.getInt("y", 0))
    }

    private fun applyLayout() {
        if (!ready) return
        fillLp()
        wm.updateViewLayout(ring, lp)
    }

    private fun wifiLevel(): Int {
        try {
            val cm = getSystemService(ConnectivityManager::class.java)
            val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return -1
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return -1
            val w = applicationContext.getSystemService(WifiManager::class.java)
            @Suppress("DEPRECATION")
            val rssi = w.connectionInfo.rssi
            return w.calculateSignalLevel(rssi)
        } catch (e: Exception) {
            return -1
        }
    }

    private fun render() {
        if (!ready) return
        ring.update(pct, charging, sig, wifiLevel())
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        // START_STICKY ile yeniden başladıysa gizlemeyi tazele
        if (this::prefs.isInitialized && prefs.getBoolean("gizle", false)) DurumCubugu.uygula(this)
        return START_STICKY
    }
    override fun onBind(i: Intent?): IBinder? = null

    override fun onDestroy() {
        h.removeCallbacks(tick)
        if (ready) {
            unregisterReceiver(battRx)
            prefs.unregisterOnSharedPreferenceChangeListener(prefL)
            telCb?.let { getSystemService(TelephonyManager::class.java).unregisterTelephonyCallback(it) }
            wm.removeView(ring)
        }
        // Servis durunca durum çubuğu simgeleri geri gelsin (orijinal değer neyse o)
        DurumCubugu.geriGetir(this)
        super.onDestroy()
    }
}
