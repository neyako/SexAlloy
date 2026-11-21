package io.github.nexalloy.morphe.twitter.link.unshorten

import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.twitter.utils.X_MODELS_PACKAGE
import io.github.nexalloy.morphe.twitter.utils.X_NAVIGATION_PACKAGE
import io.github.nexalloy.morphe.twitter.utils.dataClassToString

internal object UrlEntityToStringFingerprint : Fingerprint(
    name = "toString",
    custom = { dataClassToString("UrlEntity", X_MODELS_PACKAGE) },
)

internal object OpenExternalUrlFingerprint : Fingerprint(
    returnType = "Z",
    parameters = listOf("Ljava/lang/String;", "Ljava/lang/String;", "Z"),
    strings = listOf(
        "ExternalScreenNav",
        "Unable to start Intent",
        "No activity found for Intent",
    ),
)

private const val SECURITY_EXCEPTION_LOG = "SecurityException opening external app"

// NexAlloy: 12.45 moved this log into a Uri helper that the (String) entry point calls, so accept it in the
// method itself or in a direct callee.
internal val openExternalBrowserFingerprint = findMethodDirect {
    OpenExternalUrlFingerprint().declaredClass!!.methods.filter { method ->
        method.returnTypeName == "boolean" &&
            method.paramTypeNames == listOf("java.lang.String") &&
            (SECURITY_EXCEPTION_LOG in method.usingStrings ||
                method.invokes.any { SECURITY_EXCEPTION_LOG in it.usingStrings })
    }.single()
}

internal object OpenUrlInAppFingerprint : Fingerprint(
    classFingerprint = OpenExternalUrlFingerprint,
    returnType = "V",
    strings = listOf("com.twitter.android.debug"),
)

internal object LinkWithPostDetailArgsToStringFingerprint : Fingerprint(
    name = "toString",
    custom = { dataClassToString("LinkWithPostDetailArgs", X_NAVIGATION_PACKAGE) },
)

internal object WebViewArgsToStringFingerprint : Fingerprint(
    name = "toString",
    custom = { dataClassToString("WebViewArgs", X_NAVIGATION_PACKAGE) },
)
