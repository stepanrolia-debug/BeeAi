package com.bee.answer

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.*

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("bee", MODE_PRIVATE)
        val p = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(p, p * 2, p, p) }

        fun text(s: String, size: Float = 16f) = TextView(this).apply { text = s; textSize = size; setPadding(0, p / 2, 0, p / 2) }

        root.addView(text("🐝 Bee", 28f))
        root.addView(text(
            "Як користуватись (3 кроки):\n\n" +
            "1. Встав ключ Gemini нижче і натисни «Зберегти».\n\n" +
            "2. Натисни «Дозволити обмежені налаштування» → вгорі справа три крапки → «Дозволити обмежені налаштування». " +
            "Потім натисни «Увімкнути Bee» → знайди Bee → увімкни → «Дозволити».\n\n" +
            "3. На екрані з'явиться бджола-кнопка. Торкнись її — вона стане активною і сама шукатиме відповіді. " +
            "Торкнись ще раз — вимкнеться. Кнопку можна перетягувати."))

        val key = EditText(this).apply { hint = "Ключ Gemini (AQ...)"; setText(prefs.getString("key", "")); setSingleLine() }
        val model = EditText(this).apply { hint = "Модель"; setText(prefs.getString("model", "gemini-2.5-flash")); setSingleLine() }
        root.addView(key); root.addView(model)

        fun btn(t: String, onClick: () -> Unit) = Button(this).apply { text = t; setOnClickListener { onClick() } }

        root.addView(btn("Зберегти") {
            prefs.edit().putString("key", key.text.toString().trim()).putString("model", model.text.toString().trim()).apply()
            Toast.makeText(this, "Збережено ✅", Toast.LENGTH_SHORT).show()
        })
        root.addView(btn("Дозволити обмежені налаштування") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        })
        root.addView(btn("Увімкнути Bee") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        setContentView(ScrollView(this).apply { addView(root, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) })
    }
}
