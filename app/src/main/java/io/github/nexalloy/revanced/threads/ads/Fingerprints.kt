package io.github.nexalloy.revanced.threads.ads

import io.github.nexalloy.RequireAppVersion
import io.github.nexalloy.morphe.findMethodDirect

val adFetchSponsoredContentFingerprint = findMethodDirect {
    findMethod {
        matcher {
            returnType = "void"
            usingStrings("onFetchSponsoredContent")
        }
    }.single()
}

val adContentDeliveredFingerprint = findMethodDirect {
    findMethod {
        matcher {
            usingStrings("onSponsoredContentDelivered")
        }
    }.single()
}

private const val GONE_IN_456 = "455.99.99.99.99"

// The three label composables and the spool handler below are found by Compose trace strings and a class name.
// 456 has neither: no trace strings are left and the labels are plain obfuscated composables with nothing else to key
// on, so they stay unmatched there. HideAdsPatch skips a miss.
@get:RequireAppVersion(maxVersion = GONE_IN_456)
val paidPartnershipLabelFingerprint = findMethodDirect {
    findMethod {
        matcher {
            returnType = "void"
            usingStrings("feed.post.ui.PaidPartnershipLabel (PaidPartnershipLabel.kt:")
        }
    }.single()
}

@get:RequireAppVersion(maxVersion = GONE_IN_456)
val adMetadataFingerprint = findMethodDirect {
    findMethod {
        matcher {
            returnType = "void"
            usingStrings("sponsored.ui.AdMetadata (AdMetadata.kt:")
        }
    }.single()
}

@get:RequireAppVersion(maxVersion = GONE_IN_456)
val sponsoredLabelInHeaderFingerprint = findMethodDirect {
    findMethod {
        matcher {
            returnType = "void"
            usingStrings("sponsored.ui.SponsoredLabelInHeader (SponsoredLabel.kt:")
        }
    }.single()
}

// 456 has no spool handler class left (the feed repository logs spoolEnabled=false), so this does not match there.
@get:RequireAppVersion(maxVersion = GONE_IN_456)
val spoolAdInjectorFingerprint = findMethodDirect {
    findMethod {
        matcher {
            declaredClass = "com.instagram.barcelona.feed.data.cache.BarcelonaSpoolFeedCacheHandler"
            returnType = "boolean"
            paramCount = 2
        }
    }.single()
}

val permalinkAdResultFingerprint = findMethodDirect {
    findMethod {
        matcher {
            declaredClass = "com.instagram.barcelona.sponsored.permalink.ThreadsPermalinkAdFetcher"
            returnType = "java.lang.Object"
            usingStrings("text_feed/threads_permalink_ads/")
        }
    }.single().invokes.single { it.isConstructor && it.paramTypeNames == listOf("java.util.List", "int") }
}
