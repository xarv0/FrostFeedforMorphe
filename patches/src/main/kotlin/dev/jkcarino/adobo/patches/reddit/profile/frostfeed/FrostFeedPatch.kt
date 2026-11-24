package dev.jkcarino.adobo.patches.reddit.profile.frostfeed

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.patch
import dev.jkcarino.adobo.patches.reddit.misc.firebase.spoofCertificateHashPatch
import dev.jkcarino.adobo.patches.reddit.shared.COMPATIBILITY_REDDIT

@Suppress("unused")
val frostFeedArchivePatch = patch(
    name = "Archive profile view (FrostFeed)",
    description = "Adds a native Archive view to Reddit user profiles powered by the public Arctic Shift API."
) {
    compatibleWith(COMPATIBILITY_REDDIT)

    extendWith("extensions/reddit/frontpage.mpe")

    dependsOn(spoofCertificateHashPatch)

    execute {
        ProfileDetailsScreenConstructorFingerprint.method.apply {
            val superCallIndex =
                ProfileDetailsScreenConstructorFingerprint.instructionMatches.first().index

            addInstruction(
                index = superCallIndex + 1,
                smaliInstructions =
                    "invoke-static {p0, p1}, " +
                        "$EXTENSION_CLASS_DESCRIPTOR->registerProfileScreen(Ljava/lang/Object;Landroid/os/Bundle;)V"
            )
        }

        BaseScreenOnAttachFingerprint.method.addInstruction(
            index = 0,
            smaliInstructions =
                "invoke-static {p0, p1}, " +
                    "$EXTENSION_CLASS_DESCRIPTOR->onScreenAttached(Ljava/lang/Object;Landroid/view/View;)V"
        )

        BaseScreenOnDetachFingerprint.method.addInstruction(
            index = 0,
            smaliInstructions =
                "invoke-static {p0, p1}, " +
                    "$EXTENSION_CLASS_DESCRIPTOR->onScreenDetached(Ljava/lang/Object;Landroid/view/View;)V"
        )
    }
}
