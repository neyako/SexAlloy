package io.github.nexalloy

import com.google.gson.GsonBuilder
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.name

object FingerprintTestReport {
    data class Result(
        val fingerprint: String,
        val status: String,
        val matches: List<String> = emptyList(),
        val duration_ms: Long? = null,
        val reason: String? = null,
        val error: String? = null,
    )

    data class Group(
        val package_name: String,
        val results: MutableList<Result> = mutableListOf(),
        var completed: Boolean = false,
        var error: String? = null,
    )

    data class Apk(
        val apk: String,
        val app: String,
        var version: String? = null,
        val groups: MutableList<Group> = mutableListOf(),
    )

    private val apks = linkedMapOf<String, Apk>()
    private var startedAt = Instant.now().toString()

    /**
     * A supported app: the APK file name prefix that selects it, its id in the report and in
     * `@TargetApp`, and the source folders (under `io/github/nexalloy`) holding its fingerprints.
     */
    class TestApp(val prefix: String, val id: String, vararg val roots: String)

    val testApps = listOf(
        TestApp("com.google.android.youtube", "youtube", "morphe/youtube", "morphe/shared"),
        TestApp("com.google.android.apps.youtube.music", "music", "morphe/music", "morphe/shared"),
        TestApp("com.reddit.frontpage", "reddit", "morphe/reddit"),
        TestApp("com.zhiliaoapp.musically", "tiktok", "morphe/tiktok"),
        TestApp("com.ss.android.ugc.trill", "tiktok", "morphe/tiktok"),
        TestApp("com.twitter.android", "twitter", "morphe/twitter"),
        TestApp("com.google.android.googlequicksearchbox", "discover", "morphe/google"),
        TestApp("com.google.android.gm", "gmail", "mrxsin/gmail"),
        TestApp("com.google.android.apps.photos", "googlephotos", "revanced/googlephotos"),
        TestApp("com.microblink.photomath", "photomath", "revanced/photomath"),
        TestApp("com.strava", "strava", "revanced/strava"),
        TestApp("com.alltrails.alltrails", "alltrails", "hoodles/morphe/alltrails"),
        TestApp("com.soundcloud.android", "soundcloud", "v4n1x/morphe/soundcloud"),
        TestApp("ch.protonvpn.android", "protonvpn", "hoodles/morphe/protonvpn"),
        // Meta apps share fingerprints: the Tigon network layer and MQTT presence.
        TestApp("com.instagram.android", "instagram", "revanced/instagram", "revanced/shared/tigon"),
        TestApp("com.instagram.barcelona", "threads", "revanced/threads", "revanced/shared/tigon"),
        TestApp("com.facebook.orca", "messenger", "revanced/messenger", "revanced/shared/presence"),
        TestApp("com.facebook.katana", "facebook", "revanced/facebook", "revanced/shared/presence"),
        TestApp("com.zing.zalo", "zalo", "revanced/zalo"),
    )

    fun testAppFor(path: Path): TestApp? = testApps.firstOrNull { path.name.startsWith(it.prefix) }

    fun appFor(path: Path): String? = testAppFor(path)?.id

    fun start(paths: List<Path>) {
        startedAt = Instant.now().toString()
        apks.clear()
        paths.forEach { path ->
            appFor(path)?.let { app ->
                val key = path.toAbsolutePath().normalize().toString()
                apks[key] = Apk(key, app)
            }
        }
        write()
    }

    fun forApk(path: Path, version: AppVersion): Apk =
        apks.getValue(path.toAbsolutePath().normalize().toString()).apply {
            this.version = version.versionString
        }

    fun write() {
        val complete = apks.isNotEmpty() && apks.values.all { apk ->
            apk.version != null && apk.groups.isNotEmpty() && apk.groups.all { it.completed }
        }
        val report = linkedMapOf(
            "schema_version" to 1,
            "started_at" to startedAt,
            "written_at" to Instant.now().toString(),
            "complete" to complete,
            "apks" to apks.values.toList(),
        )
        val file = Path.of(System.getProperty(
            "fingerprints.report", "build/reports/fingerprints/results.json"
        )).toFile()
        file.parentFile.mkdirs()
        file.writer().use {
            GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(report, it)
        }
    }
}
