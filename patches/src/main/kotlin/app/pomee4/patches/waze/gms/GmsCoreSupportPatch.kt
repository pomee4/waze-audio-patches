package app.pomee4.patches.waze.gms

import app.morphe.patcher.StringComparisonType
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.matchAllMethodIndicesForEach
import app.morphe.patcher.util.returnEarly
import app.morphe.patcher.util.smali.ExternalLabel
import app.pomee4.patches.waze.gms.GmsConstants.ACTIONS
import app.pomee4.patches.waze.gms.GmsConstants.AUTHORITIES
import app.pomee4.patches.waze.gms.GmsConstants.PERMISSIONS
import app.pomee4.patches.waze.shared.Constants.COMPATIBILITY_WAZE
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21c
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference
import org.w3c.dom.Element

// Adapted from the GmsCore support patch of Morphe Patches (GPLv3), which is based on ReVanced Patches.
// Unlike the YouTube patch, Waze keeps its package name (com.waze), so only the Play Services side is redirected.

private const val WAZE_PACKAGE_NAME = "com.waze"

/** SHA-1 of the certificate the Play Store version of Waze is signed with. */
private const val WAZE_SIGNATURE = "35b438fe1bc69d975dc8702dc16ab69ebf65f26f"

/** Vendor id of ReVanced/Morphe GmsCore (package app.revanced.android.gms). */
private const val GMS_CORE_VENDOR_GROUP_ID = "app.revanced"
private const val GMS_CORE_PACKAGE_NAME = "$GMS_CORE_VENDOR_GROUP_ID.android.gms"

/** `CredentialProviderPlayServicesImpl.MIN_GMS_APK_VERSION`, the base requirement of androidx.credentials. */
private const val CREDENTIALS_BASE_MIN_GMS_VERSION = 230815045

private val gmsCoreSupportResourcePatch = resourcePatch {
    execute {
        val manifestFile = get("AndroidManifest.xml")
        manifestFile.writeText(
            manifestFile.readText().replace(
                "com.google.android.c2dm",
                "$GMS_CORE_VENDOR_GROUP_ID.android.c2dm",
            )
        )

        document("AndroidManifest.xml").use { document ->
            val manifest = document.documentElement
            val application = document.getElementsByTagName("application").item(0) as Element

            fun addMetaData(name: String, value: String) {
                application.appendChild(
                    document.createElement("meta-data").apply {
                        setAttribute("android:name", name)
                        setAttribute("android:value", value)
                    }
                )
            }

            // GmsCore treats the re-signed app as the original Waze.
            addMetaData("$GMS_CORE_PACKAGE_NAME.SPOOFED_PACKAGE_NAME", WAZE_PACKAGE_NAME)
            addMetaData("$GMS_CORE_PACKAGE_NAME.SPOOFED_PACKAGE_SIGNATURE", WAZE_SIGNATURE)
            addMetaData("app.revanced.MICROG_PACKAGE_NAME", GMS_CORE_PACKAGE_NAME)

            // Android 11+ package visibility, needed to bind to GmsCore services.
            val queries = document.getElementsByTagName("queries").item(0) as Element?
                ?: document.createElement("queries").also { manifest.appendChild(it) }
            queries.appendChild(
                document.createElement("package").apply {
                    setAttribute("android:name", GMS_CORE_PACKAGE_NAME)
                }
            )
        }
    }
}

@Suppress("unused")
val gmsCoreSupportPatch = bytecodePatch(
    name = "GmsCore support",
    description = "Uses GmsCore (app.revanced.android.gms) instead of Google Play Services, " +
        "so Google sign-in and other Google services work in the re-signed app. GmsCore must be installed.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_WAZE)

    dependsOn(gmsCoreSupportResourcePatch)

    execute {
        // Report Play Services as available.
        GooglePlayUtilityFingerprint.method.returnEarly(0)
        ServiceCheckFingerprint.methodOrNull?.returnEarly()

        // On Android 14+ androidx.credentials goes through the system Credential Manager, which asks
        // Google Play Services. Without the system provider it falls back to the Play Services library,
        // whose calls are redirected to GmsCore above.
        CredentialsFrameworkProviderFingerprint.methodOrNull?.addInstructions(
            0,
            """
                const/4 v0, 0x0
                return-object v0
            """,
        )

        // Google sign-in through androidx.credentials uses the identitycredentials API of newer Play Services,
        // which GmsCore does not implement, so the request would end up in Google Play Services.
        // Report only the base Play Services version as available, so the older BeginSignIn/GetSignInIntent
        // APIs (implemented by GmsCore) are used instead.
        CredentialsIsAvailableOnDeviceFingerprint.methodOrNull?.apply {
            addInstructionsWithLabels(
                0,
                """
                    const v0, $CREDENTIALS_BASE_MIN_GMS_VERSION
                    if-le p1, v0, :original
                    const/4 v0, 0x0
                    return v0
                """,
                ExternalLabel("original", getInstruction(0)),
            )
        }

        fun replaceStrings(fromString: String, toString: String) {
            string(fromString).matchAllMethodIndicesForEach(requireMatches = false) { index ->
                val register = getInstruction<OneRegisterInstruction>(index).registerA
                replaceInstruction(
                    index,
                    BuilderInstruction21c(
                        Opcode.CONST_STRING,
                        register,
                        ImmutableStringReference(toString),
                    )
                )
            }
        }

        replaceStrings("com.google", GMS_CORE_VENDOR_GROUP_ID)
        replaceStrings("com.google.android.gms", GMS_CORE_PACKAGE_NAME)

        (PERMISSIONS + ACTIONS + AUTHORITIES).forEach { fromString ->
            replaceStrings(fromString, fromString.replace("com.google", GMS_CORE_VENDOR_GROUP_ID))
        }

        // content:// URIs of Play Services content providers.
        val authorityUriPrefixes = AUTHORITIES.map { it to "content://$it" }
        string("content://", StringComparisonType.STARTS_WITH)
            .matchAllMethodIndicesForEach(requireMatches = false) { index ->
                val instruction = getInstruction<ReferenceInstruction>(index)
                val uri = (instruction.reference as StringReference).string
                val (authority, prefix) = authorityUriPrefixes.firstOrNull { (_, prefix) -> uri.startsWith(prefix) }
                    ?: return@matchAllMethodIndicesForEach

                replaceInstruction(
                    index,
                    BuilderInstruction21c(
                        Opcode.CONST_STRING,
                        (instruction as OneRegisterInstruction).registerA,
                        ImmutableStringReference(
                            uri.replace(prefix, "content://${authority.replace("com.google", GMS_CORE_VENDOR_GROUP_ID)}")
                        ),
                    )
                )
            }
    }
}
