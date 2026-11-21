package io.github.nexalloy.mrxsin.gmail.ads

import io.github.nexalloy.morphe.findMethodListDirect
import org.luckypray.dexkit.query.enums.StringMatchType

// Gmail moved its ad views from the first package to the second in late 2026.
val AD_PACKAGES = listOf("com.google.android.gm.ads", "com.google.android.apps.gmail.libraries.ads")

val adViewConstructors = findMethodListDirect {
    AD_PACKAGES.flatMap { adPackage ->
        findMethod {
            matcher {
                declaredClass { className("$adPackage.", StringMatchType.StartsWith) }
                name = "<init>"
                paramTypes(listOf("android.content.Context", "android.util.AttributeSet"))
            }
        }
    }
}
