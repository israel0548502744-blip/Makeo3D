package com.triposr.mobile

import android.content.Context
import org.json.JSONObject
import java.util.Locale

/**
 * Hebrew or English. The UI text in the code is Hebrew; in English each string is looked up in
 * assets/i18n/en.json (keyed by the Hebrew text, written by convert/i18n_wrap.py). The language is
 * chosen once at launch: the phone's language unless the user picked one in Settings.
 */
object I18n {
    enum class Choice { AUTO, HE, EN }

    @Volatile var english = false; private set
    private var en: Map<String, String> = emptyMap()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun choice(ctx: Context) = runCatching { Choice.valueOf(prefs(ctx).getString("language", "AUTO")!!) }.getOrDefault(Choice.AUTO)

    /** Saved for the next launch: strings already shown (and enum labels) keep the old language. */
    fun setChoice(ctx: Context, c: Choice) = prefs(ctx).edit().putString("language", c.name).commit()

    fun init(ctx: Context) {
        english = when (choice(ctx)) {
            Choice.HE -> false
            Choice.EN -> true
            Choice.AUTO -> Locale.getDefault().language !in setOf("he", "iw")
        }
        if (english) en = runCatching {
            val j = JSONObject(ctx.assets.open("i18n/en.json").bufferedReader().readText())
            j.keys().asSequence().associateWith { j.getString(it) }
        }.getOrDefault(emptyMap())
    }

    fun lookup(he: String) = if (english) en[he] ?: he else he
}

/** UI text: the Hebrew [he] or its English translation. */
fun tr(he: String): String = I18n.lookup(he)

/** UI text with {0}, {1}… filled in after translating. */
fun tr(he: String, vararg args: Any?): String {
    var s = I18n.lookup(he)
    args.forEachIndexed { i, a -> s = s.replace("{$i}", a.toString()) }
    return s
}
