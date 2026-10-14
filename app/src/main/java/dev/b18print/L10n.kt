package dev.b18print

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import androidx.annotation.StringRes
import dev.b18print.data.Store
import java.util.Locale

/**
 * App language handling.
 *
 * Translations are plain Android resources: English in `res/values/strings.xml`,
 * every other language in `res/values-<code>/strings.xml`. The build scans those folders
 * ([BuildConfig.LANGUAGES]), so a new folder appears in the language picker automatically.
 *
 * The setting [LANGUAGE_SYSTEM] follows the phone language and falls back to English.
 */
object L10n {
    const val LANGUAGE_SYSTEM = "system"

    /** Language codes with a translation, e.g. ["de", "en"]. */
    val available: List<String> = BuildConfig.LANGUAGES.split(',').filter { it.isNotBlank() }

    private var cache: Pair<String, Context>? = null

    /** The configured language code, or null when following the system. */
    fun configured(): String? = Store.settings.value.language.takeIf { it != LANGUAGE_SYSTEM && it in available }

    /** Locale used for strings and date formatting. */
    fun locale(): Locale = configured()?.let { Locale.forLanguageTag(it) } ?: systemLocale()

    /** Phone language if a translation exists, otherwise English. */
    fun systemLocale(): Locale {
        // The app configuration also reflects Android 13+ per-app language settings.
        val sys = (if (Store.isInitialized) Store.appContext.resources else Resources.getSystem()).configuration.locales
        for (i in 0 until sys.size()) {
            val l = sys[i]
            if (l.language in available.map { Locale.forLanguageTag(it).language }) return l
        }
        return Locale.ENGLISH
    }

    /** Wraps a context so its resources use the configured language. */
    fun wrap(base: Context): Context {
        val lang = configured() ?: return base
        val cfg = Configuration(base.resources.configuration)
        cfg.setLocales(LocaleList(Locale.forLanguageTag(lang)))
        return base.createConfigurationContext(cfg)
    }

    /** Localized context for code outside of Compose (view model, renderer, printer state). */
    fun context(): Context {
        val key = Store.settings.value.language
        cache?.let { (k, c) -> if (k == key) return c }
        return wrap(Store.appContext).also { cache = key to it }
    }

    fun str(@StringRes id: Int, vararg args: Any): String = context().getString(id, *args)

    /** Native name of a language, e.g. "Deutsch". */
    fun displayName(code: String): String {
        val l = Locale.forLanguageTag(code)
        return l.getDisplayName(l).replaceFirstChar { it.titlecase(l) }
    }
}
