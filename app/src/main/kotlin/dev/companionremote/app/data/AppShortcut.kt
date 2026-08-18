package dev.companionremote.app.data

/** A button that launches an Android TV app ID or deep-link URI. */
data class AppShortcut(
    val id: String,
    val label: String,
    val target: String,
)

/** Stable defaults used on first run and to repair an invalid saved layout. */
object AppShortcutDefaults {
    val all: List<AppShortcut> = listOf(
        AppShortcut("zee5", "ZEE5", "https://www.zee5.com/"),
        AppShortcut("youtube", "YouTube", "https://www.youtube.com"),
        AppShortcut("prime_video", "Prime Video", "https://www.primevideo.com/"),
        AppShortcut("jiohotstar", "JioHotstar", "https://www.hotstar.com/"),
    )
}
