package app.pomee4.patches.waze.gms

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags

// Fingerprints of the Play Services client library bundled in the app, adapted from Morphe Patches (GPLv3).

/**
 * `GooglePlayServicesUtilLight.isGooglePlayServicesAvailable(Context, int)`.
 *
 * Unlike YouTube, Waze does not inline the "MetadataValueReader" helper into this method,
 * so it is matched by the Play Store check instead.
 */
internal object GooglePlayUtilityFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "I",
    parameters = listOf("Landroid/content/Context;", "I"),
    filters = listOf(
        string("com.google.android.gms"),
        string(" requires the Google Play Store, but it is missing."),
    )
)

/**
 * Throws if Play Services is unavailable. Not present in Waze 5.26.5.0, where the availability check above covers it.
 */
internal object ServiceCheckFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "V",
    parameters = listOf("L", "I"),
    strings = listOf("Google Play Services not available"),
)
