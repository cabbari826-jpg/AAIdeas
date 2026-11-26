package com.aaideas.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.UserHandle
import android.os.UserManager
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

/** One launchable activity. [key] is stable across restarts (component + user serial). */
data class AppEntry(
    val key: String,
    val label: String,
    val component: ComponentName,
    val user: UserHandle?,
) {
    val packageName: String get() = component.packageName
}

/** What the home screen shows: dock, the four apps on page one, and the rest in pages. */
data class HomeLayout(
    val dock: List<AppEntry>,
    val favourites: List<AppEntry>,
    val rest: List<AppEntry>,
) {
    fun pages(perPage: Int): List<List<AppEntry>> = rest.chunked(perPage)

    companion object {
        /**
         * Pure arrangement so it can be tested: dock = phone, messages, browser, camera;
         * page one = gallery, mail, maps, music; everything else alphabetically.
         * Hidden apps are left out everywhere.
         */
        fun arrange(
            all: List<AppEntry>,
            dockPackages: List<String?>,
            favouritePackages: List<String?>,
            hidden: Set<String>,
        ): HomeLayout {
            val visible = all.filter { it.key !in hidden }
            val used = HashSet<String>()
            fun pick(pkgs: List<String?>) = pkgs.mapNotNull { p ->
                visible.firstOrNull { it.packageName == p && it.key !in used }?.also { used += it.key }
            }
            val dock = pick(dockPackages)
            val fav = pick(favouritePackages)
            val collator = Collator.getInstance(Locale("ru")).apply { strength = Collator.PRIMARY }
            val rest = visible.filter { it.key !in used }.sortedWith { a, b -> collator.compare(a.label, b.label) }
            return HomeLayout(dock, fav, rest)
        }
    }
}

/** Loads installed apps through LauncherApps and keeps the list fresh on installs/removals. */
class AppRepository(private val context: Context, private val scope: CoroutineScope) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val pm = context.packageManager

    var apps by mutableStateOf<List<AppEntry>>(emptyList()); private set
    var dockPackages by mutableStateOf<List<String?>>(emptyList()); private set
    var favouritePackages by mutableStateOf<List<String?>>(emptyList()); private set

    /** Processed glass icons, filled in the background as they are made. */
    val icons = mutableStateMapOf<String, GlassIcon>()
    private val infos = HashMap<String, LauncherActivityInfo>()
    private var iconJob: Job? = null
    var iconSizePx = 168

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = reload(packageName)
        override fun onPackageAdded(packageName: String, user: UserHandle) = reload(packageName)
        override fun onPackageChanged(packageName: String, user: UserHandle) = reload(packageName)
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reload(null)
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reload(null)
    }

    fun start() {
        launcherApps.registerCallback(callback)
        reload(null)
    }

    fun stop() = launcherApps.unregisterCallback(callback)

    private fun reload(changedPackage: String?) {
        if (changedPackage != null) icons.keys.filter { it.startsWith("$changedPackage/") }.forEach { icons.remove(it) }
        scope.launch {
            val list = withContext(Dispatchers.Default) { load() }
            apps = list
            dockPackages = listOf(
                resolve(Intent(Intent.ACTION_DIAL)),
                resolve(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING))
                    ?: resolve(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))),
                resolve(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER))
                    ?: resolve(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))),
                resolve(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)),
            )
            favouritePackages = listOf(
                resolve(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY)),
                resolve(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_EMAIL)),
                resolve(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MAPS)),
                resolve(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC)),
            )
            makeIcons()
        }
    }

    private fun load(): List<AppEntry> {
        val out = ArrayList<AppEntry>()
        synchronized(infos) { infos.clear() }
        for (user in userManager.userProfiles) {
            val serial = userManager.getSerialNumberForUser(user)
            for (info in launcherApps.getActivityList(null, user)) {
                if (info.componentName.packageName == context.packageName) continue
                val key = "${info.componentName.flattenToShortString()}#$serial"
                synchronized(infos) { infos[key] = info }
                out += AppEntry(key, info.label?.toString()?.trim().orEmpty().ifEmpty { info.componentName.packageName }, info.componentName, user)
            }
        }
        return out
    }

    /** Package of the default app for [intent], or null when there is none or only a chooser. */
    private fun resolve(intent: Intent): String? {
        val ri = runCatching { pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) }.getOrNull()
            ?: return null
        val pkg = ri.activityInfo?.packageName ?: return null
        return pkg.takeIf { it != "android" && !ri.activityInfo.name.contains("Resolver") }
    }

    private fun makeIcons() {
        iconJob?.cancel()
        val size = iconSizePx
        iconJob = scope.launch(Dispatchers.Default) {
            val todo = synchronized(infos) { infos.toMap() }.filterKeys { it !in icons }
            for ((key, info) in todo) {
                val drawable = runCatching { info.getIcon(context.resources.displayMetrics.densityDpi) }.getOrNull() ?: continue
                val icon = runCatching { IconProcessor.process(drawable, size) }.getOrNull() ?: continue
                withContext(Dispatchers.Main) { icons[key] = icon }
            }
        }
    }

    fun launch(app: AppEntry, bounds: Rect?) {
        runCatching { launcherApps.startMainActivity(app.component, app.user, bounds, null) }
    }

    fun openDetails(app: AppEntry) {
        runCatching { launcherApps.startAppDetailsActivity(app.component, app.user, null, null) }
    }

    fun uninstall(app: AppEntry) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /** App shortcuts (e.g. "New message"); only a default launcher may read them. */
    fun shortcuts(app: AppEntry): List<ShortcutInfo> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return emptyList()
        if (!runCatching { launcherApps.hasShortcutHostPermission() }.getOrDefault(false)) return emptyList()
        val q = LauncherApps.ShortcutQuery()
            .setPackage(app.packageName)
            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST)
        return runCatching { launcherApps.getShortcuts(q, app.user ?: android.os.Process.myUserHandle()) }
            .getOrNull().orEmpty()
            .sortedBy { it.rank }
            .take(2)
    }

    fun startShortcut(s: ShortcutInfo) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
        runCatching { launcherApps.startShortcut(s, null, null) }
    }
}
