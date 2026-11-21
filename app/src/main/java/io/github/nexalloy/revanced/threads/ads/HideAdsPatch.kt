package io.github.nexalloy.revanced.threads.ads

import app.morphe.extension.shared.Logger
import de.robv.android.xposed.XC_MethodReplacement
import io.github.nexalloy.patch

val HideAds = patch(
    name = "Hide ads",
    description = "Hides injected ads, sponsored content, and paid partnership posts in Threads feed, " +
        "and the ads shown among the replies when you open a post."
) {
    ::adFetchSponsoredContentFingerprint.hookMethod(XC_MethodReplacement.DO_NOTHING)
    ::adContentDeliveredFingerprint.hookMethod(XC_MethodReplacement.DO_NOTHING)
    ::permalinkAdResultFingerprint.hookMethod {
        before { param ->
            param.args[0] = emptyList<Any>()
            param.args[1] = 0
        }
    }

    // Leftovers that only matter when an ad gets past the hooks above. The label composables lost every string and
    // class name they were found by and the spool cache is gone in 456, so a miss here must not undo the rest.
    listOf(::paidPartnershipLabelFingerprint, ::adMetadataFingerprint, ::sponsoredLabelInHeaderFingerprint).forEach { label ->
        runCatching { label.hookMethod(XC_MethodReplacement.DO_NOTHING) }
            .onFailure { Logger.printInfo { "Threads: ${label.name} not found, the ad label stays: $it" } }
    }
    runCatching { ::spoolAdInjectorFingerprint.hookMethod(XC_MethodReplacement.returnConstant(true)) }
        .onFailure { Logger.printInfo { "Threads: ${::spoolAdInjectorFingerprint.name} not found, no spool to hook: $it" } }
}
