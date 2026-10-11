package com.batuhan.island

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("island", MODE_PRIVATE)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        fun btn(t: String, f: () -> Unit) {
            col.addView(Button(this).apply { text = t; setOnClickListener { f() } })
        }

        fun slider(label: String, key: String, maxV: Int, def: Int) {
            col.addView(TextView(this).apply { text = label })
            col.addView(SeekBar(this).apply {
                max = maxV
                progress = prefs.getInt(key, def)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                        prefs.edit().putInt(key, p).apply()
                    }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })
            })
        }

        btn("1) Üstte gösterme izni") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        btn("2) Telefon + bildirim izni") {
            requestPermissions(arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        btn("3) Başlat") { startForegroundService(Intent(this, IslandService::class.java)) }
        btn("Durdur") { stopService(Intent(this, IslandService::class.java)) }
        slider("Dikey konum (dp)", "y", 60, 0)
        slider("Yatay konum (200 = tam orta, sağa doğru artar)", "x", 400, 230)
        slider("Boyut (dp)", "size", 60, 32)

        setContentView(ScrollView(this).apply { addView(col) })
    }
}
