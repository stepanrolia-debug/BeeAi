package com.bee.answer

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs

class BeeService : AccessibilityService() {

    private val ui = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager
    private var btn: ImageView? = null
    private var status: TextView? = null
    private val markers = ArrayList<ImageView>()
    private lateinit var frames: List<Bitmap>
    private lateinit var bmpOn: Bitmap
    private lateinit var bmpOff: Bitmap
    private lateinit var bmpMain: Bitmap

    private var active = false
    private var busy = false
    private var animating = false
    private var frameIdx = 0
    private var gen = 0
    private var lastSig: IntArray? = null
    private var screenW = 1080
    private var screenH = 2340

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun load(id: Int) = BitmapFactory.decodeResource(resources, id)
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    override fun onServiceConnected() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        frames = listOf(
            R.drawable.bee_yellow_frame_1, R.drawable.bee_yellow_frame_2, R.drawable.bee_yellow_frame_3,
            R.drawable.bee_yellow_frame_4, R.drawable.bee_yellow_frame_5, R.drawable.bee_yellow_frame_6
        ).map { load(it) }
        bmpOn = load(R.drawable.bee_activ)
        bmpOff = load(R.drawable.bee_unactiv)
        bmpMain = load(R.drawable.bee_main)
        addButton()
        addStatus()
    }

    private fun addStatus() {
        val t = TextView(this)
        t.setTextColor(Color.WHITE)
        t.textSize = 14f
        t.maxWidth = resources.displayMetrics.widthPixels * 9 / 10
        t.setBackgroundColor(0xCC000000.toInt())
        t.setPadding(dp(10), dp(6), dp(10), dp(6))
        t.visibility = View.GONE
        val lp = params(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, false)
        lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        lp.y = dp(90)
        wm.addView(t, lp)
        status = t
    }

    private fun setStatus(s: String?) {
        val t = status ?: return
        if (s == null) { t.visibility = View.GONE } else { t.text = "[v2] " + s; t.alpha = 1f; t.visibility = View.VISIBLE }
    }

    private fun params(w: Int, h: Int, touchable: Boolean): WindowManager.LayoutParams {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (!touchable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        return WindowManager.LayoutParams(
            w, h, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, flags, PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
    }

    private fun addButton() {
        val v = ImageView(this)
        v.setImageBitmap(bmpOff)
        v.scaleType = ImageView.ScaleType.FIT_CENTER
        val lp = params(dp(72), dp(72), true)
        lp.x = dp(8); lp.y = dp(240)
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; startX = lp.x; startY = lp.y; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    val mx = e.rawX - downX; val my = e.rawY - downY
                    if (!moved && (abs(mx) > slop || abs(my) > slop)) moved = true
                    if (moved) { lp.x = startX + mx.toInt(); lp.y = startY + my.toInt(); wm.updateViewLayout(v, lp) }
                }
                MotionEvent.ACTION_UP -> if (!moved) toggle()
            }
            true
        }
        wm.addView(v, lp)
        btn = v
    }

    private fun toggle() {
        active = !active
        gen++
        ui.removeCallbacksAndMessages(null)
        animating = false
        busy = false
        lastSig = null
        clearMarkers()
        setStatus(null)
        if (active) {
            btn?.setImageBitmap(bmpOn)
            toast("Bee увімкнено 🐝")
            ui.post { cycle() }
        } else {
            btn?.setImageBitmap(bmpOff)
            toast("Bee вимкнено")
        }
    }

    private fun cycle() {
        if (!active || busy) return
        busy = true
        val g = gen
        markers.forEach { it.visibility = View.INVISIBLE }
        status?.alpha = 0f
        ui.postDelayed({ if (g == gen) shoot(g) }, 250)
    }

    private fun finish(g: Int, delay: Long) {
        if (g != gen) return
        busy = false
        if (active) ui.postDelayed({ cycle() }, delay)
    }

    private fun shoot(g: Int) {
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(r: ScreenshotResult) {
                val hw = r.hardwareBuffer
                val bmp = Bitmap.wrapHardwareBuffer(hw, r.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
                hw.close()
                if (bmp == null || g != gen) { setStatus("❌ Знімок екрана порожній"); finish(g, 3000); return }
                handleShot(bmp, g)
            }
            override fun onFailure(errorCode: Int) {
                setStatus("❌ Знімок екрана не вдався (код $errorCode)")
                finish(g, 5000)
            }
        })
    }

    private fun sigOf(bmp: Bitmap): IntArray {
        val s = Bitmap.createScaledBitmap(bmp, 16, 32, true)
        val a = IntArray(16 * 32)
        s.getPixels(a, 0, 16, 0, 0, 16, 32)
        return a
    }

    private fun same(a: IntArray, b: IntArray): Boolean {
        var d = 0L
        for (i in a.indices) {
            d += abs((a[i] and 0xFF) - (b[i] and 0xFF)) + abs(((a[i] shr 8) and 0xFF) - ((b[i] shr 8) and 0xFF)) +
                abs(((a[i] shr 16) and 0xFF) - ((b[i] shr 16) and 0xFF))
        }
        return d / a.size < 12
    }

    private fun handleShot(bmp: Bitmap, g: Int) {
        screenW = bmp.width; screenH = bmp.height
        val sig = sigOf(bmp)
        val prev = lastSig
        if (prev != null && same(prev, sig)) {
            markers.forEach { it.visibility = View.VISIBLE }
            status?.alpha = 1f
            finish(g, 3000)
            return
        }
        lastSig = sig
        setStatus("🤔 Gemini думає…")
        startAnim(g)
        Thread {
            var res: List<Answer>? = null
            var err: String? = null
            try { res = askGemini(bmp) } catch (e: Exception) { err = e.message ?: e.toString() }
            ui.post {
                if (g != gen) return@post
                animating = false
                btn?.setImageBitmap(bmpOn)
                clearMarkers()
                if (err != null) { setStatus("❌ " + err.take(220)); lastSig = null; finish(g, 6000) }
                else {
                    val list = res ?: emptyList()
                    if (list.isEmpty()) setStatus("Gemini не знайшов тест на екрані")
                    else setStatus("✅ Відповідь: " + list.joinToString(", ") { it.letter })
                    showMarkers(list)
                    finish(g, 3000)
                }
            }
        }.start()
    }

    private fun startAnim(g: Int) {
        animating = true
        frameIdx = 0
        ui.post(object : Runnable {
            override fun run() {
                if (!animating || g != gen) return
                btn?.setImageBitmap(frames[frameIdx++ % frames.size])
                ui.postDelayed(this, 200)
            }
        })
    }

    private fun clearMarkers() {
        markers.forEach { try { wm.removeView(it) } catch (_: Exception) {} }
        markers.clear()
    }

    private class Answer(val letter: String, val box: IntArray?)

    private fun showMarkers(answers: List<Answer>) {
        val size = dp(36)
        for (a in answers) {
            val b = a.box ?: continue
            val v = ImageView(this)
            v.setImageBitmap(bmpMain)
            val lp = params(size, size, false)
            lp.x = (b[3] * screenW / 1000 + dp(4)).coerceIn(0, screenW - size)
            lp.y = (((b[0] + b[2]) / 2) * screenH / 1000 - size / 2).coerceIn(0, screenH - size)
            wm.addView(v, lp)
            markers.add(v)
        }
    }

    private val prompt = """На скріншоті екрана телефона може бути тест з варіантами відповідей (А, Б, В, Г або A, B, C, D або 1, 2, 3...). Для КОЖНОГО повністю видимого питання вибери єдину правильну відповідь. Поверни ТІЛЬКИ JSON без пояснень у форматі: {"answers":[{"letter":"Б","box_2d":[ymin,xmin,ymax,xmax]}]} де box_2d - рамка рядка тексту правильного варіанту відповіді, координати від 0 до 1000. Якщо на екрані немає тесту - поверни {"answers":[]}."""

    private fun askGemini(bmp: Bitmap): List<Answer> {
        val prefs = getSharedPreferences("bee", MODE_PRIVATE)
        val key = (prefs.getString("key", "") ?: "").trim()
        if (key.isEmpty()) throw Exception("Немає ключа Gemini. Відкрий додаток Bee і встав ключ.")
        val model = (prefs.getString("model", "") ?: "").trim().ifEmpty { "gemini-2.5-flash" }
        val bos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, bos)
        val b64 = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray()
                .put(JSONObject().put("text", prompt))
                .put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", b64))))))
            .put("generationConfig", JSONObject().put("responseMimeType", "application/json").put("temperature", 0))
        val con = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .openConnection() as HttpURLConnection
        con.requestMethod = "POST"
        con.doOutput = true
        con.connectTimeout = 20000
        con.readTimeout = 90000
        con.setRequestProperty("Content-Type", "application/json")
        con.setRequestProperty("x-goog-api-key", key)
        con.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = con.responseCode
        val text = (if (code in 200..299) con.inputStream else con.errorStream).bufferedReader().readText()
        if (code != 200) throw Exception("Gemini помилка $code: " + text.take(150))
        val parts = JSONObject(text).getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts")
        val sb = StringBuilder()
        for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text"))
        val clean = sb.toString().replace("```json", "").replace("```", "").trim()
        val arr = try { JSONObject(clean).optJSONArray("answers") } catch (e: Exception) {
            throw Exception("Не розібрав відповідь Gemini: " + clean.take(100))
        } ?: return emptyList()
        val out = ArrayList<Answer>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val b = o.optJSONArray("box_2d")
            val box = if (b != null && b.length() == 4) IntArray(4) { b.optInt(it) } else null
            out.add(Answer(o.optString("letter", "?"), box))
        }
        return out
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        ui.removeCallbacksAndMessages(null)
        clearMarkers()
        try { status?.let { wm.removeView(it) } } catch (_: Exception) {}
        try { btn?.let { wm.removeView(it) } } catch (_: Exception) {}
        super.onDestroy()
    }
}
