package io.github.nexalloy.hoodles.morphe.alltrails.pro

import io.github.nexalloy.morphe.Fingerprint

object IsProFingerprint : Fingerprint(
    name = "isPro",
    returnType = "Z",
)

// NexAlloy: 26.11.30 has a second getSubscriptionTier() in an unrelated upsell action model, so stay in the user model.
object GetSubscriptionTierFingerprint : Fingerprint(
    classFingerprint = IsProFingerprint,
    name = "getSubscriptionTier",
    returnType = "Ljava/lang/String;",
)
