package dev.saketanand.setwise.timer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Whether the app still has to ask for the notification permission (rest countdown and alert). */
fun interface NotificationPermission {
    fun needsAsking(): Boolean
}

/** Android 13+ only asks at runtime; older versions have notifications on unless turned off in settings. */
class AndroidNotificationPermission(private val context: Context) : NotificationPermission {
    override fun needsAsking(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
}
