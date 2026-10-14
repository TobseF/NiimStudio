package dev.b18print.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.provider.OpenableColumns
import dev.b18print.L10n
import dev.b18print.R
import dev.b18print.model.AppSettings
import dev.b18print.model.LabelDoc
import dev.b18print.model.LabelProfile
import dev.b18print.model.Template
import dev.b18print.model.newId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** App-wide persistence: settings, label profiles, templates, draft, imported images and fonts. */
object Store {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
    }

    lateinit var appContext: Context
        private set

    val isInitialized get() = ::appContext.isInitialized

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings

    private val _profiles = MutableStateFlow(LabelProfile.defaults())
    val profiles: StateFlow<List<LabelProfile>> = _profiles

    private val _templates = MutableStateFlow<List<Template>>(emptyList())
    val templates: StateFlow<List<Template>> = _templates

    private val dir get() = appContext.filesDir
    val imagesDir get() = File(dir, "images").apply { mkdirs() }
    val fontsDir get() = File(dir, "fonts").apply { mkdirs() }
    private val templatesDir get() = File(dir, "templates").apply { mkdirs() }

    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        readJson<AppSettings>(File(dir, "settings.json"))?.let { _settings.value = it }
        _profiles.value = readJson<List<LabelProfile>>(File(dir, "profiles.json"))?.takeIf { it.isNotEmpty() } ?: localizedDefaults()
        _templates.value = templatesDir.listFiles { f -> f.extension == "json" }.orEmpty()
            .mapNotNull { readJson<Template>(it) }
            .sortedByDescending { it.updated }
    }

    private inline fun <reified T> readJson(f: File): T? = try {
        if (f.exists()) json.decodeFromString<T>(f.readText()) else null
    } catch (e: Exception) {
        android.util.Log.w("Store", "Cannot read $f: ${e.message}")
        null
    }

    private inline fun <reified T> writeJson(f: File, value: T) {
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(json.encodeToString(value))
        tmp.renameTo(f)
    }

    // ---- settings

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val s = transform(_settings.value)
        _settings.value = s
        writeJson(File(dir, "settings.json"), s)
    }

    // ---- profiles

    fun saveProfile(p: LabelProfile) {
        val list = _profiles.value.toMutableList()
        val i = list.indexOfFirst { it.id == p.id }
        if (i >= 0) list[i] = p else list += p
        setProfiles(list)
    }

    fun deleteProfile(id: String) {
        val list = _profiles.value.filterNot { it.id == id }
        setProfiles(list.ifEmpty { localizedDefaults() })
    }

    fun resetProfiles() = setProfiles(localizedDefaults())

    fun localizedDefaults() = LabelProfile.defaults(
        L10n.str(R.string.profile_white), L10n.str(R.string.profile_cable), L10n.str(R.string.profile_continuous),
    )

    private fun setProfiles(list: List<LabelProfile>) {
        _profiles.value = list
        writeJson(File(dir, "profiles.json"), list)
    }

    fun profile(id: String): LabelProfile? = _profiles.value.firstOrNull { it.id == id }

    // ---- templates

    fun saveTemplate(t: Template) {
        writeJson(File(templatesDir, "${t.id}.json"), t)
        _templates.value = (_templates.value.filterNot { it.id == t.id } + t).sortedByDescending { it.updated }
    }

    fun deleteTemplate(id: String) {
        File(templatesDir, "$id.json").delete()
        _templates.value = _templates.value.filterNot { it.id == id }
    }

    // ---- draft (the label currently in the editor)

    fun saveDraft(doc: LabelDoc) = try {
        writeJson(File(dir, "draft.json"), doc)
    } catch (e: Exception) {
        android.util.Log.w("Store", "Draft save failed: ${e.message}")
    }

    fun loadDraft(): LabelDoc? = readJson(File(dir, "draft.json"))

    // ---- imported assets

    /** Copies a picked image into app storage (downscaled, alpha flattened to white). Returns the file name. */
    fun importImage(uri: Uri, maxSide: Int = 1600): String {
        val cr = appContext.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val src = cr.openInputStream(uri).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error(L10n.str(R.string.err_image_unreadable))
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        Canvas(out).apply {
            drawColor(Color.WHITE)
            drawBitmap(src, 0f, 0f, null)
        }
        val name = "${newId()}.png"
        File(imagesDir, name).outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return name
    }

    /** Copies a picked TTF/OTF into app storage. Returns the font key. */
    fun importFont(uri: Uri): String {
        val cr = appContext.contentResolver
        var display = cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: "font-${newId()}.ttf"
        display = display.replace(Regex("[^A-Za-z0-9._ -]"), "_")
        if (!display.lowercase().endsWith(".ttf") && !display.lowercase().endsWith(".otf")) display += ".ttf"
        val f = File(fontsDir, display)
        cr.openInputStream(uri).use { input -> f.outputStream().use { input!!.copyTo(it) } }
        // Validate: throws if it is not a font.
        android.graphics.Typeface.createFromFile(f)
        return "file:${f.name}"
    }

    fun readText(uri: Uri): String =
        appContext.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
}
