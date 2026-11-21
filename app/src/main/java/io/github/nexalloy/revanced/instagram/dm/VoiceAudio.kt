package io.github.nexalloy.revanced.instagram.dm

import java.lang.reflect.Method

/**
 * Finds a voice message's download URL: the Media it carries, that Media's audio info,
 * then the one web address among the info's text values.
 */
internal class VoiceAudio(voiceClass: Class<*>, private val audioInfo: Method) {
    private val media = voiceClass.declaredFields.single { it.type == audioInfo.declaringClass }.apply { isAccessible = true }
    private val texts = audioInfo.returnType.methods.filter { it.parameterCount == 0 && it.returnType == String::class.java }

    fun url(voice: Any): String? {
        val info = media.get(voice)?.let { audioInfo.invoke(it) } ?: return null
        return texts.firstNotNullOfOrNull { text ->
            (runCatching { text.invoke(info) }.getOrNull() as? String)?.takeIf { it.startsWith("http") }
        }
    }
}
