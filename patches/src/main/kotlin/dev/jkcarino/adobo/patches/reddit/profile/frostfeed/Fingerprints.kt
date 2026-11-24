package dev.jkcarino.adobo.patches.reddit.profile.frostfeed

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags

internal const val EXTENSION_CLASS_DESCRIPTOR =
    "Ldev/jkcarino/extension/reddit/frontpage/frostfeed/FrostFeedHook;"

internal object ProfileDetailsScreenConstructorFingerprint : Fingerprint(
    definingClass = "Lcom/reddit/profile/ui/screens/detail/ProfileDetailsScreen;",
    name = "<init>",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    parameters = listOf("Landroid/os/Bundle;"),
    returnType = "V",
    filters = listOf(
        methodCall(
            definingClass = "Lcom/reddit/screen/ComposeScreen;",
            name = "<init>",
            parameters = listOf("Landroid/os/Bundle;")
        )
    )
)

internal object BaseScreenOnAttachFingerprint : Fingerprint(
    definingClass = "Lcom/reddit/screen/BaseScreen;",
    returnType = "V",
    parameters = listOf("Landroid/view/View;"),
    filters = listOf(
        string("onAttach")
    )
)

internal object BaseScreenOnDetachFingerprint : Fingerprint(
    definingClass = "Lcom/reddit/screen/BaseScreen;",
    returnType = "V",
    parameters = listOf("Landroid/view/View;"),
    filters = listOf(
        string("onDetach")
    )
)
