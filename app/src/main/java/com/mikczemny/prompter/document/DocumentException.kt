package com.mikczemny.prompter.document

import java.io.IOException

/**
 * A document import failure with a machine-readable [reason], so the message
 * shown to the user can be localized at the UI boundary rather than baked into
 * English here. Extends [IOException] so existing catch sites and tests that
 * treat extraction failures as IO errors keep working unchanged.
 *
 * The extractors deliberately carry no [Context] and no `R` reference — reason
 * codes keep this layer free of app resources; [DocumentImporter] turns them
 * into text.
 */
internal class DocumentException(val reason: Reason) : IOException(reason.name) {
    enum class Reason {
        /** The picked PDF is encrypted and cannot be opened without a password. */
        PDF_PASSWORD_PROTECTED,

        /** The archive opened but is not a WordprocessingML document. */
        NOT_A_WORD_DOCUMENT,

        /** The Word body was found but could not be parsed. */
        UNREADABLE_WORD_DOCUMENT,
    }
}
