package io.github.nugggy.makeupmirror.ui

import android.content.Intent
import io.github.nugggy.makeupmirror.update.UpdateChecker

/** Android share sheet with a link that always downloads the newest APK. */
fun shareAppIntent(): Intent {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, "Makeup Mirror")
        .putExtra(Intent.EXTRA_TEXT, "Try Makeup Mirror on your Android phone: ${UpdateChecker.SHARE_URL}")
    return Intent.createChooser(send, "Share Makeup Mirror")
}
