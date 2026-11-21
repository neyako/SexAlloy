package io.github.nexalloy.revanced.facebook.ad

import io.github.nexalloy.hookMethod
import io.github.nexalloy.patch

// Plugins are numbered from 0, far fewer than this.
private const val MAX_PILL_PLUGINS = 64
private val SHOP_CTAS = setOf("AFFILIATE_BANNER", "AFFILIATE_EYEBROW", "PRODUCT_TAGGING", "PRODUCT_TAGGING_V2", "STOREFRONT", "SHOP_SIMILAR")

private fun withoutShopCtas(ctas: Collection<*>): List<*>? =
    ctas.filterNot { it is Enum<*> && it.name in SHOP_CTAS }.takeIf { it.size != ctas.size }

val HideAffiliateLinks = patch(
    name = "Hide affiliate product links",
    description = "Removes the shopping cards that affiliate links add to reels, feed posts and comments, " +
        "plus Shop now cards on reels. The \"Commission eligible\" label stays.",
) {
    ::affiliateReelCardCheckFingerprint.method.hookMethod { after { param -> if (param.result == true) param.result = false } }
    ::affiliateFooterIdFingerprint.method.hookMethod { after { param -> param.result = null } }

    val card = ::affiliateCommentCardFingerprint.method
    if (card.declaringClass.name == FLOATING_CARD_PLUGIN) {
        card.hookMethod { after { param -> param.result = null } }
    } else {
        val names = ::affiliateCommentCardNameFingerprint.method.apply { isAccessible = true }
        val number = (0..MAX_PILL_PLUGINS).firstOrNull { runCatching { names.invoke(null, it) }.getOrNull() == FLOATING_CARD_PLUGIN }
        check(number != null) { "affiliate card is not in the comment pill plugin names" }
        card.hookMethod { after { param -> if (param.args.last() == number) param.result = false } }
    }

    val filter = ::affiliateCtaFilterFingerprint.method
    val candidates = filter.parameterTypes.indexOfFirst { Collection::class.java.isAssignableFrom(it) }
    check(candidates >= 0) { "card filter takes no list" }
    filter.hookMethod {
        before { param -> (param.args[candidates] as? Collection<*>)?.let(::withoutShopCtas)?.let { param.args[candidates] = it } }
        after { param -> (param.result as? List<*>)?.let(::withoutShopCtas)?.let { param.result = it } }
    }
}
