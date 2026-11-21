package io.github.nexalloy.revanced.zalo.story

import io.github.nexalloy.morphe.findMethodDirect

// The request command ids identify the senders. 26.11 strips Kotlin null-check strings
// such as the "storyId" parameter name.
private const val CMD_STORY_SEEN = 1343
private const val CMD_STORY_SEEN_SUGGESTED = 1356

val storySeenRequestFingerprint = findMethodDirect {
    findMethod {
        matcher {
            usingNumbers(CMD_STORY_SEEN)
            paramTypes("int", "java.lang.String")
            returnType = "void"
        }
    }.single()
}

val storySeenSuggestedRequestFingerprint = findMethodDirect {
    findMethod {
        matcher {
            usingNumbers(CMD_STORY_SEEN_SUGGESTED)
            paramTypes("int", "int", "java.lang.String")
            returnType = "void"
        }
    }.single()
}
