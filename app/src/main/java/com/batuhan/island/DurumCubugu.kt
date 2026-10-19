package com.batuhan.island

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * Durum çubuğunun sağ tarafındaki pil / Wi-Fi / şebeke simgelerini gizler.
 *
 * Android'de normal uygulamalar SystemUI'ye çizemez; SystemUI'nin okuduğu
 * `Settings.Secure.icon_blacklist` anahtarı yazılır. Bunun için
 * WRITE_SECURE_SETTINGS izni gerekir (bir kerelik ADB ya da root):
 *
 *   adb shell pm grant com.batuhan.island android.permission.WRITE_SECURE_SETTINGS
 *
 * Orijinal değer ilk uygulamada saklanır; servis durunca aynen geri yazılır.
 * Böylece kullanıcının kendi "Sistem UI Tuner" ayarları bozulmaz.
 */
object DurumCubugu {
    private const val ANAHTAR = "icon_blacklist"
    private const val PREFS = "island"
    private const val ESKI = "blacklist_onceki"
    private const val KAYIT = "blacklist_kaydedildi"

    /** Sağ üstte görünen ve halkanın zaten gösterdiği simgeler.
     *  "mobile_gen" bazı üretici ROM'larında kullanılan eşdeğer anahtar. */
    val gizlenecek = listOf("battery", "wifi", "mobile", "mobile_gen")

    fun izinVarMi(c: Context): Boolean =
        c.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    /** Simgeleri gizler. İzin yoksa false döner ve hiçbir şey yazmaz. */
    fun uygula(c: Context): Boolean {
        if (!izinVarMi(c)) return false
        val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return try {
            val cr = c.contentResolver
            if (!p.getBoolean(KAYIT, false)) {
                p.edit()
                    .putString(ESKI, Settings.Secure.getString(cr, ANAHTAR) ?: "")
                    .putBoolean(KAYIT, true)
                    .apply()
            }
            val mevcut = Settings.Secure.getString(cr, ANAHTAR) ?: ""
            val kume = mevcut.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toMutableSet()
            kume.addAll(gizlenecek)
            Settings.Secure.putString(cr, ANAHTAR, kume.joinToString(","))
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Gizlemeyi geri alır: saklanan orijinal değeri (ya da boş değeri) yazar. */
    fun geriGetir(c: Context): Boolean {
        val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.getBoolean(KAYIT, false)) return false
        if (!izinVarMi(c)) return false
        return try {
            Settings.Secure.putString(c.contentResolver, ANAHTAR, p.getString(ESKI, "") ?: "")
            p.edit().remove(ESKI).putBoolean(KAYIT, false).apply()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun uygulanmisMi(c: Context): Boolean =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KAYIT, false)
}
