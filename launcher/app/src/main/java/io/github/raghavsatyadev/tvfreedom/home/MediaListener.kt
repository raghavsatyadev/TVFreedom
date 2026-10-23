// Ported from 0xRampey/material-tv-launcher (Apache-2.0); see launcher/NOTICE.
package io.github.raghavsatyadev.tvfreedom.home

import android.service.notification.NotificationListenerService

/**
 * Exists only so Android lets the home screen see which app is playing media (for the Now Playing card).
 * It does not read, store or send notifications.
 */
class MediaListener : NotificationListenerService()
