package io.github.tobsef.niimstudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Checks every translation (res/values-<lang>/strings.xml) against the English default:
 * same keys, same format placeholders, same plural and array entries.
 * Run it after adding or editing a language.
 */
class TranslationsTest {

    private val res = File("src/main/res")
    private val placeholder = Regex("%(\\d+\\$)?[sdf]")

    private class Res(
        val strings: Map<String, String>,
        val untranslatable: Set<String>,
        val plurals: Map<String, Map<String, String>>,
        val arrays: Map<String, List<String>>,
    )

    private fun parse(f: File): Res {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f)
        val strings = LinkedHashMap<String, String>()
        val untranslatable = HashSet<String>()
        val plurals = HashMap<String, Map<String, String>>()
        val arrays = HashMap<String, List<String>>()
        val root = doc.documentElement
        val nodes = root.childNodes
        for (i in 0 until nodes.length) {
            val n = nodes.item(i) as? Element ?: continue
            val name = n.getAttribute("name")
            when (n.tagName) {
                "string" -> {
                    strings[name] = n.textContent
                    if (n.getAttribute("translatable") == "false") untranslatable += name
                }
                "plurals" -> plurals[name] = children(n).associate { it.getAttribute("quantity") to it.textContent }
                "string-array" -> arrays[name] = children(n).map { it.textContent }
            }
        }
        return Res(strings, untranslatable, plurals, arrays)
    }

    private fun children(e: Element) = (0 until e.childNodes.length).mapNotNull { e.childNodes.item(it) as? Element }

    private fun placeholders(s: String) = placeholder.findAll(s.replace("%%", "")).map { it.value }.sorted().toList()

    private fun translations() = res.listFiles().orEmpty()
        .filter { it.isDirectory && Regex("values-[a-z]{2,3}(-r[A-Z]{2})?").matches(it.name) && File(it, "strings.xml").exists() }

    @Test
    fun everyTranslationIsComplete() {
        val base = parse(File(res, "values/strings.xml"))
        val dirs = translations()
        assertTrue("no translations found", dirs.isNotEmpty())
        val problems = mutableListOf<String>()
        for (dir in dirs) {
            val t = parse(File(dir, "strings.xml"))
            val lang = dir.name
            for ((k, v) in base.strings) {
                if (k in base.untranslatable) continue
                val tv = t.strings[k]
                if (tv == null) {
                    problems += "$lang: missing string '$k'"
                } else if (placeholders(v) != placeholders(tv)) {
                    problems += "$lang: placeholders differ in '$k' (${placeholders(v)} vs ${placeholders(tv)})"
                }
            }
            for (k in t.strings.keys - base.strings.keys) problems += "$lang: unknown string '$k'"
            for ((k, v) in base.plurals) {
                val tv = t.plurals[k]
                if (tv == null) problems += "$lang: missing plurals '$k'"
                else if (tv["other"] == null) problems += "$lang: plurals '$k' needs quantity 'other'"
                else if (placeholders(v["other"]!!) != placeholders(tv["other"]!!)) problems += "$lang: placeholders differ in plurals '$k'"
            }
            for ((k, v) in base.arrays) {
                val tv = t.arrays[k]
                if (tv == null) problems += "$lang: missing array '$k'"
                else if (tv.size != v.size) problems += "$lang: array '$k' has ${tv.size} items, expected ${v.size}"
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun languageListMatchesResourceFolders() {
        val expected = (listOf("en") + translations().map { it.name.removePrefix("values-").replace("-r", "-") }).sorted()
        assertEquals(expected, BuildConfig.LANGUAGES.split(',').sorted())
    }
}
