package evola.composeapp.language

import androidx.compose.runtime.compositionLocalOf
import evola.shared.language.NativeLanguage

/** The active goal's chosen native language, provided once near the composition root (see
 * [evola.composeapp.main.MainScreen]) from `goal.nativeLanguage`. Drives [evola.composeapp.rtl.RtlText]'s
 * RTL-vs-LTR/font choice - defaults to [NativeLanguage.ARABIC] only as a placeholder before a real
 * goal is loaded; every real read happens after a goal (and therefore a native language) exists. */
val LocalNativeLanguage = compositionLocalOf { NativeLanguage.ARABIC }
