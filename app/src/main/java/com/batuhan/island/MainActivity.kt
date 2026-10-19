package com.batuhan.island

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var izinYazi: TextView
    private var kilit = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("island", MODE_PRIVATE)

        // Uygulama/servis düştüyse durum çubuğu gizli kalmasın:
        // halka çalışmıyorken gizleme anlamsız, eski hâle dön.
        if (DurumCubugu.uygulanmisMi(this) && !servisCalisiyor()) {
            DurumCubugu.geriGetir(this)
            prefs.edit().putBoolean("gizle", false).apply()
        }

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

        // ---- durum çubuğu (sağ üst) simgelerini gizleme ----
        izinYazi = TextView(this).apply {
            textSize = 12f
            setPadding(0, pad, 0, pad / 2)
        }
        col.addView(izinYazi)

        val kutu = CheckBox(this).apply {
            text = "Sağ üstteki simgeleri gizle (pil · Wi-Fi · şebeke)"
            isChecked = prefs.getBoolean("gizle", false)
        }
        kutu.setOnCheckedChangeListener { _, checked ->
            if (kilit) return@setOnCheckedChangeListener
            if (checked && !DurumCubugu.izinVarMi(this@MainActivity)) {
                kilit = true
                kutu.isChecked = false
                kilit = false
                izinDialog()
                return@setOnCheckedChangeListener
            }
            prefs.edit().putBoolean("gizle", checked).apply()
            if (checked) DurumCubugu.uygula(this@MainActivity) else DurumCubugu.geriGetir(this@MainActivity)
            izinDurumuYenile()
        }
        col.addView(kutu)

        btn("Durum çubuğu simgelerini geri getir") {
            DurumCubugu.geriGetir(this)
            prefs.edit().putBoolean("gizle", false).apply()
            kutu.isChecked = false
            izinDurumuYenile()
        }
        btn("ADB izin komutunu göster") { izinDialog() }

        slider("Dikey konum (dp)", "y", 60, 0)
        slider("Yatay konum (200 = tam orta, sağa doğru artar)", "x", 400, 230)
        slider("Boyut (dp)", "size", 60, 32)

        setContentView(ScrollView(this).apply { addView(col) })
        izinDurumuYenile()
    }

    override fun onResume() {
        super.onResume()
        if (::izinYazi.isInitialized) izinDurumuYenile()
    }

    private fun izinDurumuYenile() {
        if (!::izinYazi.isInitialized) return
        izinYazi.text = if (DurumCubugu.izinVarMi(this))
            "Yetki: verildi ✓ — gizleme kullanılabilir"
        else
            "Yetki: verilmedi — gizlemek için bir kereliğine ADB ile izin verin"
    }

    private fun izinDialog() {
        val komut = "adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS"
        AlertDialog.Builder(this)
            .setTitle("Durum çubuğu izni")
            .setMessage(
                "Android, normal uygulamaların durum çubuğunu değiştirmesine izin vermez.\n\n" +
                    "Bir kereliğine bilgisayardan şu komutu çalıştırın (USB hata ayıklama açık olmalı):\n\n" +
                    komut +
                    "\n\nSonra kutuyu işaretleyin. İzin, uygulama kaldırılana kadar geçerli kalır " +
                    "ve yeniden başlatmalarda kaybolmaz."
            )
            .setPositiveButton("Kopyala") { _, _ -> kopyala(komut) }
            .setNegativeButton("Kapat", null)
            .show()
    }

    private fun kopyala(metin: String) {
        val pano = getSystemService(ClipboardManager::class.java) ?: return
        pano.setPrimaryClip(ClipData.newPlainText("komut", metin))
    }

    /** Yalnızca kendi servisimiz listelenir (ActivityManager belgesi). */
    @Suppress("DEPRECATION")
    private fun servisCalisiyor(): Boolean {
        val am = getSystemService(ActivityManager::class.java) ?: return false
        return am.getRunningServices(1000).any { it.service.className == IslandService::class.java.name }
    }
}
