package dev.saketanand.setwise.timer

/**
 * Posts the rest countdown notification again. Needed right after the user allows notifications:
 * the first rest starts together with the permission prompt, so its notification was posted
 * before the permission existed and Android dropped it.
 */
fun interface RestNotificationRefresher {
    /** Does nothing when no rest is running. */
    fun refresh()
}
