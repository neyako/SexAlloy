package io.github.nexalloy.hoodles.morphe.protonvpn.upselling

import io.github.nexalloy.patch

private const val ONBOARDING = "ONBOARDING"

// NexAlloy: 5.21.57 has no launchOnboarding(Context) wrapper (its callers call the shared launcher directly),
// so filter that launcher by its ONBOARDING arguments.
internal val SkipUpgradeOnboarding = patch {
    ::upgradeDialogLauncherFingerprint.hookMethod {
        before { param ->
            val source = param.args[1] as? Enum<*>
            val trigger = param.args[2] as? Enum<*>
            if (source?.name == ONBOARDING || trigger?.name == ONBOARDING) param.result = null
        }
    }
}
