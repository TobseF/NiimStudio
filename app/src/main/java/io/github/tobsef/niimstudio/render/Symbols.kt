package io.github.tobsef.niimstudio.render

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import io.github.tobsef.niimstudio.L10n
import io.github.tobsef.niimstudio.R
import io.github.tobsef.niimstudio.data.Store
import java.util.Locale

/**
 * A library symbol. The name resource holds "Name|search tags"; only the part before
 * the bar is displayed.
 */
class SymbolDef(val key: String, @StringRes val nameRes: Int, val category: String, val icon: ImageVector) {
    val name: String get() = L10n.str(nameRes).substringBefore('|')
}

/** Symbol library built on Material icons (Apache 2.0), drawn as vector paths for crisp printing. */
object Symbols {
    /** Category id to its title resource, in display order. */
    val categories: List<Pair<String, Int>> = listOf("arrows" to R.string.cat_arrows, "signs" to R.string.cat_signs, "household" to R.string.cat_household, "kitchen" to R.string.cat_kitchen, "tech" to R.string.cat_tech, "office" to R.string.cat_office, "nature" to R.string.cat_nature, "people" to R.string.cat_people, "traffic" to R.string.cat_traffic, "notices" to R.string.cat_notices, "leisure" to R.string.cat_leisure)

    private fun s(cat: String, @StringRes name: Int, icon: ImageVector) = SymbolDef("mi:${icon.name}", name, cat, icon)

    val all: List<SymbolDef> by lazy {
        listOf(
            s("arrows", R.string.sym_arrow_up, Icons.Filled.ArrowUpward),
            s("arrows", R.string.sym_arrow_down, Icons.Filled.ArrowDownward),
            s("arrows", R.string.sym_arrow_right, Icons.AutoMirrored.Filled.ArrowForward),
            s("arrows", R.string.sym_arrow_left, Icons.AutoMirrored.Filled.ArrowBack),
            s("arrows", R.string.sym_arrow_long, Icons.AutoMirrored.Filled.ArrowRightAlt),
            s("arrows", R.string.sym_north_east, Icons.Filled.NorthEast),
            s("arrows", R.string.sym_north_west, Icons.Filled.NorthWest),
            s("arrows", R.string.sym_south_east, Icons.Filled.SouthEast),
            s("arrows", R.string.sym_south_west, Icons.Filled.SouthWest),
            s("arrows", R.string.sym_double_arrow, Icons.Filled.DoubleArrow),
            s("arrows", R.string.sym_chevron_up, Icons.Filled.KeyboardArrowUp),
            s("arrows", R.string.sym_chevron_down, Icons.Filled.KeyboardArrowDown),
            s("arrows", R.string.sym_triangle_up, Icons.Filled.ArrowDropUp),
            s("arrows", R.string.sym_triangle_down, Icons.Filled.ArrowDropDown),
            s("arrows", R.string.sym_swap, Icons.Filled.SwapHoriz),
            s("arrows", R.string.sym_swap_vertical, Icons.Filled.SwapVert),
            s("arrows", R.string.sym_cycle, Icons.Filled.Autorenew),
            s("arrows", R.string.sym_refresh, Icons.Filled.Refresh),
            s("arrows", R.string.sym_undo, Icons.AutoMirrored.Filled.Undo),
            s("arrows", R.string.sym_trend_up, Icons.AutoMirrored.Filled.TrendingUp),
            s("arrows", R.string.sym_trend_down, Icons.AutoMirrored.Filled.TrendingDown),
            s("arrows", R.string.sym_height, Icons.Filled.Height),
            s("arrows", R.string.sym_open, Icons.AutoMirrored.Filled.OpenInNew),
            s("signs", R.string.sym_check, Icons.Filled.Check),
            s("signs", R.string.sym_check_circle, Icons.Filled.CheckCircle),
            s("signs", R.string.sym_cross, Icons.Filled.Close),
            s("signs", R.string.sym_cancel, Icons.Filled.Cancel),
            s("signs", R.string.sym_plus, Icons.Filled.Add),
            s("signs", R.string.sym_minus, Icons.Filled.Remove),
            s("signs", R.string.sym_star, Icons.Filled.Star),
            s("signs", R.string.sym_star_outline, Icons.Filled.StarBorder),
            s("signs", R.string.sym_heart, Icons.Filled.Favorite),
            s("signs", R.string.sym_heart_outline, Icons.Filled.FavoriteBorder),
            s("signs", R.string.sym_info, Icons.Filled.Info),
            s("signs", R.string.sym_question, Icons.AutoMirrored.Filled.Help),
            s("signs", R.string.sym_forbidden, Icons.Filled.Block),
            s("signs", R.string.sym_circle, Icons.Filled.Circle),
            s("signs", R.string.sym_circle_outline, Icons.Filled.RadioButtonUnchecked),
            s("signs", R.string.sym_square, Icons.Filled.CropSquare),
            s("signs", R.string.sym_triangle, Icons.Filled.ChangeHistory),
            s("signs", R.string.sym_bookmark, Icons.Filled.Bookmark),
            s("signs", R.string.sym_flag, Icons.Filled.Flag),
            s("signs", R.string.sym_hash, Icons.Filled.Tag),
            s("signs", R.string.sym_at, Icons.Filled.AlternateEmail),
            s("signs", R.string.sym_euro, Icons.Filled.Euro),
            s("signs", R.string.sym_dollar, Icons.Filled.AttachMoney),
            s("signs", R.string.sym_copyright, Icons.Filled.Copyright),
            s("signs", R.string.sym_percent, Icons.Filled.Percent),
            s("household", R.string.sym_house, Icons.Filled.Home),
            s("household", R.string.sym_bed, Icons.Filled.Bed),
            s("household", R.string.sym_sofa, Icons.Filled.Weekend),
            s("household", R.string.sym_chair, Icons.Filled.Chair),
            s("household", R.string.sym_bathtub, Icons.Filled.Bathtub),
            s("household", R.string.sym_shower, Icons.Filled.Shower),
            s("household", R.string.sym_wc, Icons.Filled.Wc),
            s("household", R.string.sym_washing_machine, Icons.Filled.LocalLaundryService),
            s("household", R.string.sym_clothes, Icons.Filled.Checkroom),
            s("household", R.string.sym_lightbulb, Icons.Filled.Lightbulb),
            s("household", R.string.sym_key, Icons.Filled.Key),
            s("household", R.string.sym_lock, Icons.Filled.Lock),
            s("household", R.string.sym_door, Icons.Filled.DoorFront),
            s("household", R.string.sym_garage, Icons.Filled.Garage),
            s("household", R.string.sym_box, Icons.Filled.Inventory2),
            s("household", R.string.sym_archive, Icons.Filled.Archive),
            s("household", R.string.sym_trash, Icons.Filled.Delete),
            s("household", R.string.sym_recycling, Icons.Filled.Recycling),
            s("household", R.string.sym_cleaning, Icons.Filled.CleaningServices),
            s("household", R.string.sym_toys, Icons.Filled.Toys),
            s("household", R.string.sym_gift, Icons.Filled.CardGiftcard),
            s("household", R.string.sym_pet, Icons.Filled.Pets),
            s("household", R.string.sym_tools, Icons.Filled.Build),
            s("household", R.string.sym_handyman, Icons.Filled.Handyman),
            s("kitchen", R.string.sym_fridge, Icons.Filled.Kitchen),
            s("kitchen", R.string.sym_coffee, Icons.Filled.LocalCafe),
            s("kitchen", R.string.sym_restaurant, Icons.Filled.Restaurant),
            s("kitchen", R.string.sym_dining, Icons.Filled.LocalDining),
            s("kitchen", R.string.sym_cake, Icons.Filled.Cake),
            s("kitchen", R.string.sym_pizza, Icons.Filled.LocalPizza),
            s("kitchen", R.string.sym_ice_cream, Icons.Filled.Icecream),
            s("kitchen", R.string.sym_wine, Icons.Filled.WineBar),
            s("kitchen", R.string.sym_drink, Icons.Filled.LocalDrink),
            s("kitchen", R.string.sym_cocktail, Icons.Filled.LocalBar),
            s("kitchen", R.string.sym_tea, Icons.Filled.EmojiFoodBeverage),
            s("kitchen", R.string.sym_bakery, Icons.Filled.BakeryDining),
            s("kitchen", R.string.sym_noodles, Icons.Filled.RamenDining),
            s("kitchen", R.string.sym_fish, Icons.Filled.SetMeal),
            s("kitchen", R.string.sym_shopping_cart, Icons.Filled.ShoppingCart),
            s("kitchen", R.string.sym_basket, Icons.Filled.ShoppingBasket),
            s("tech", R.string.sym_bluetooth, Icons.Filled.Bluetooth),
            s("tech", R.string.sym_wifi, Icons.Filled.Wifi),
            s("tech", R.string.sym_usb, Icons.Filled.Usb),
            s("tech", R.string.sym_power, Icons.Filled.Power),
            s("tech", R.string.sym_on_off, Icons.Filled.PowerSettingsNew),
            s("tech", R.string.sym_bolt, Icons.Filled.Bolt),
            s("tech", R.string.sym_battery, Icons.Filled.BatteryFull),
            s("tech", R.string.sym_charging, Icons.Filled.BatteryChargingFull),
            s("tech", R.string.sym_cable, Icons.Filled.Cable),
            s("tech", R.string.sym_router, Icons.Filled.Router),
            s("tech", R.string.sym_network, Icons.Filled.Lan),
            s("tech", R.string.sym_computer, Icons.Filled.Computer),
            s("tech", R.string.sym_laptop, Icons.Filled.Laptop),
            s("tech", R.string.sym_smartphone, Icons.Filled.Smartphone),
            s("tech", R.string.sym_tv, Icons.Filled.Tv),
            s("tech", R.string.sym_headphones, Icons.Filled.Headphones),
            s("tech", R.string.sym_speaker, Icons.Filled.Speaker),
            s("tech", R.string.sym_keyboard, Icons.Filled.Keyboard),
            s("tech", R.string.sym_mouse, Icons.Filled.Mouse),
            s("tech", R.string.sym_printer, Icons.Filled.Print),
            s("tech", R.string.sym_memory, Icons.Filled.Memory),
            s("tech", R.string.sym_sd_card, Icons.Filled.SdCard),
            s("tech", R.string.sym_cloud, Icons.Filled.Cloud),
            s("tech", R.string.sym_settings, Icons.Filled.Settings),
            s("tech", R.string.sym_camera, Icons.Filled.PhotoCamera),
            s("tech", R.string.sym_microphone, Icons.Filled.Mic),
            s("tech", R.string.sym_electrics, Icons.Filled.ElectricalServices),
            s("office", R.string.sym_email, Icons.Filled.Email),
            s("office", R.string.sym_phone, Icons.Filled.Phone),
            s("office", R.string.sym_folder, Icons.Filled.Folder),
            s("office", R.string.sym_document, Icons.Filled.Description),
            s("office", R.string.sym_task, Icons.AutoMirrored.Filled.Assignment),
            s("office", R.string.sym_scissors, Icons.Filled.ContentCut),
            s("office", R.string.sym_paperclip, Icons.Filled.AttachFile),
            s("office", R.string.sym_calendar, Icons.Filled.Event),
            s("office", R.string.sym_clock, Icons.Filled.Schedule),
            s("office", R.string.sym_alarm, Icons.Filled.Alarm),
            s("office", R.string.sym_company, Icons.Filled.Business),
            s("office", R.string.sym_work, Icons.Filled.Work),
            s("office", R.string.sym_badge, Icons.Filled.Badge),
            s("office", R.string.sym_group, Icons.Filled.Group),
            s("office", R.string.sym_qr, Icons.Filled.QrCode2),
            s("office", R.string.sym_web, Icons.Filled.Language),
            s("office", R.string.sym_location, Icons.Filled.LocationOn),
            s("office", R.string.sym_map, Icons.Filled.Map),
            s("office", R.string.sym_shop, Icons.Filled.Store),
            s("office", R.string.sym_price_tag, Icons.Filled.Sell),
            s("office", R.string.sym_offer, Icons.Filled.LocalOffer),
            s("office", R.string.sym_credit_card, Icons.Filled.CreditCard),
            s("office", R.string.sym_receipt, Icons.Filled.Receipt),
            s("office", R.string.sym_book, Icons.AutoMirrored.Filled.MenuBook),
            s("office", R.string.sym_school, Icons.Filled.School),
            s("nature", R.string.sym_sun, Icons.Filled.WbSunny),
            s("nature", R.string.sym_moon, Icons.Filled.DarkMode),
            s("nature", R.string.sym_snow, Icons.Filled.AcUnit),
            s("nature", R.string.sym_drop, Icons.Filled.WaterDrop),
            s("nature", R.string.sym_thermometer, Icons.Filled.Thermostat),
            s("nature", R.string.sym_wind, Icons.Filled.Air),
            s("nature", R.string.sym_umbrella, Icons.Filled.BeachAccess),
            s("nature", R.string.sym_tree, Icons.Filled.Park),
            s("nature", R.string.sym_forest, Icons.Filled.Forest),
            s("nature", R.string.sym_leaf, Icons.Filled.Eco),
            s("nature", R.string.sym_grass, Icons.Filled.Grass),
            s("nature", R.string.sym_flower, Icons.Filled.LocalFlorist),
            s("nature", R.string.sym_spa, Icons.Filled.Spa),
            s("nature", R.string.sym_bug, Icons.Filled.BugReport),
            s("nature", R.string.sym_mountains, Icons.Filled.Terrain),
            s("nature", R.string.sym_fire, Icons.Filled.LocalFireDepartment),
            s("nature", R.string.sym_flame, Icons.Filled.Whatshot),
            s("people", R.string.sym_person, Icons.Filled.Person),
            s("people", R.string.sym_face, Icons.Filled.Face),
            s("people", R.string.sym_smile, Icons.Filled.SentimentSatisfied),
            s("people", R.string.sym_very_happy, Icons.Filled.SentimentVerySatisfied),
            s("people", R.string.sym_sad, Icons.Filled.SentimentVeryDissatisfied),
            s("people", R.string.sym_baby, Icons.Filled.ChildCare),
            s("people", R.string.sym_wheelchair, Icons.AutoMirrored.Filled.Accessible),
            s("people", R.string.sym_family, Icons.Filled.FamilyRestroom),
            s("people", R.string.sym_walk, Icons.AutoMirrored.Filled.DirectionsWalk),
            s("people", R.string.sym_run, Icons.AutoMirrored.Filled.DirectionsRun),
            s("people", R.string.sym_fitness, Icons.Filled.FitnessCenter),
            s("people", R.string.sym_meditation, Icons.Filled.SelfImprovement),
            s("traffic", R.string.sym_car, Icons.Filled.DirectionsCar),
            s("traffic", R.string.sym_bicycle, Icons.AutoMirrored.Filled.DirectionsBike),
            s("traffic", R.string.sym_bus, Icons.Filled.DirectionsBus),
            s("traffic", R.string.sym_train, Icons.Filled.Train),
            s("traffic", R.string.sym_plane, Icons.Filled.Flight),
            s("traffic", R.string.sym_delivery, Icons.Filled.LocalShipping),
            s("traffic", R.string.sym_motorbike, Icons.Filled.TwoWheeler),
            s("traffic", R.string.sym_gas_station, Icons.Filled.LocalGasStation),
            s("traffic", R.string.sym_charging_station, Icons.Filled.EvStation),
            s("traffic", R.string.sym_parking, Icons.Filled.LocalParking),
            s("traffic", R.string.sym_anchor, Icons.Filled.Anchor),
            s("traffic", R.string.sym_sailing, Icons.Filled.Sailing),
            s("traffic", R.string.sym_rocket, Icons.Filled.RocketLaunch),
            s("notices", R.string.sym_warning, Icons.Filled.Warning),
            s("notices", R.string.sym_error, Icons.Filled.Error),
            s("notices", R.string.sym_report, Icons.Filled.Report),
            s("notices", R.string.sym_danger, Icons.Filled.Dangerous),
            s("notices", R.string.sym_important, Icons.Filled.PriorityHigh),
            s("notices", R.string.sym_no_smoking, Icons.Filled.SmokeFree),
            s("notices", R.string.sym_smoking, Icons.Filled.SmokingRooms),
            s("notices", R.string.sym_hand, Icons.Filled.FrontHand),
            s("notices", R.string.sym_first_aid, Icons.Filled.MedicalServices),
            s("notices", R.string.sym_hospital, Icons.Filled.LocalHospital),
            s("notices", R.string.sym_plaster, Icons.Filled.Healing),
            s("notices", R.string.sym_medication, Icons.Filled.Medication),
            s("notices", R.string.sym_lab, Icons.Filled.Science),
            s("notices", R.string.sym_microscope, Icons.Filled.Biotech),
            s("notices", R.string.sym_visible, Icons.Filled.Visibility),
            s("notices", R.string.sym_invisible, Icons.Filled.VisibilityOff),
            s("notices", R.string.sym_shield, Icons.Filled.Shield),
            s("notices", R.string.sym_security, Icons.Filled.Security),
            s("notices", R.string.sym_health, Icons.Filled.HealthAndSafety),
            s("notices", R.string.sym_hearing, Icons.Filled.Hearing),
            s("leisure", R.string.sym_music, Icons.Filled.MusicNote),
            s("leisure", R.string.sym_football, Icons.Filled.SportsSoccer),
            s("leisure", R.string.sym_basketball, Icons.Filled.SportsBasketball),
            s("leisure", R.string.sym_game, Icons.Filled.SportsEsports),
            s("leisure", R.string.sym_brush, Icons.Filled.Brush),
            s("leisure", R.string.sym_palette, Icons.Filled.Palette),
            s("leisure", R.string.sym_puzzle, Icons.Filled.Extension),
            s("leisure", R.string.sym_dice, Icons.Filled.Casino),
            s("leisure", R.string.sym_cinema, Icons.Filled.Theaters),
            s("leisure", R.string.sym_party, Icons.Filled.Celebration),
            s("leisure", R.string.sym_photo, Icons.Filled.Image),
        )
    }

    private val byKey by lazy { all.associateBy { it.key } }

    fun find(key: String) = byKey[key]

    private var index: Pair<String, Map<String, String>>? = null

    /** Search text per symbol: localized name and tags plus the English ones. */
    private fun searchIndex(): Map<String, String> {
        val lang = L10n.locale().toLanguageTag()
        index?.let { (l, m) -> if (l == lang) return m }
        val local = L10n.context()
        val en: Context = Store.appContext.createConfigurationContext(
            Configuration(Store.appContext.resources.configuration).apply { setLocale(Locale.ENGLISH) },
        )
        val m = all.associate { it.key to (local.getString(it.nameRes) + " " + en.getString(it.nameRes)).lowercase() }
        index = lang to m
        return m
    }

    fun search(q: String): List<SymbolDef> {
        val t = q.trim().lowercase()
        if (t.isEmpty()) return all
        val idx = searchIndex()
        return all.filter { idx[it.key]?.contains(t) == true }
    }

    private class Compiled(val vw: Float, val vh: Float, val paths: List<Path>)

    private val compiled = HashMap<String, Compiled>()

    private fun compile(v: ImageVector): Compiled {
        val out = mutableListOf<Path>()
        fun walk(g: VectorGroup, parent: Matrix) {
            val m = Matrix(parent)
            m.preTranslate(g.translationX + g.pivotX, g.translationY + g.pivotY)
            m.preRotate(g.rotation)
            m.preScale(g.scaleX, g.scaleY)
            m.preTranslate(-g.pivotX, -g.pivotY)
            for (n in g) {
                when (n) {
                    is VectorGroup -> walk(n, m)
                    is VectorPath -> {
                        val p = PathParser().addPathNodes(n.pathData).toPath().asAndroidPath()
                        p.fillType = if (n.pathFillType == PathFillType.EvenOdd) Path.FillType.EVEN_ODD else Path.FillType.WINDING
                        p.transform(m)
                        out += p
                    }
                }
            }
        }
        walk(v.root, Matrix())
        return Compiled(v.viewportWidth, v.viewportHeight, out)
    }

    /** Draws the symbol fitted (aspect kept) into a w×h box at the canvas origin. */
    fun draw(c: Canvas, key: String, w: Float, h: Float, paint: Paint) {
        val def = find(key) ?: return
        val comp = synchronized(compiled) { compiled.getOrPut(key) { compile(def.icon) } }
        val s = minOf(w / comp.vw, h / comp.vh)
        c.save()
        c.translate((w - comp.vw * s) / 2, (h - comp.vh * s) / 2)
        c.scale(s, s)
        for (p in comp.paths) c.drawPath(p, paint)
        c.restore()
    }
}
