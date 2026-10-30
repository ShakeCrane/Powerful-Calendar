package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.ui.v2.nativeui.NativeChromeSkin
import dev.powerfulcalendar.ui.v2.nativeui.NativeOverlaySkin
import dev.powerfulcalendar.ui.v2.nativeui.NativePickerSkin

/**
 * First-party Native presentation profile. Each adapter delegates to the existing Native
 * renderer exposed by its typed context, keeping one implementation of each screen while
 * making Native an explicit profile alongside other compiled skins.
 *
 * These adapters receive presentation snapshots and host callbacks only. They do not add a
 * persistence, authorization, or Android-system capability of their own.
 */
internal object NativeSkinHostRegistration {
    fun install() {
        PresentationSkinRegistry.reset()
        CompiledSkinRuntime.registerTimeline(Timeline)
        PresentationSkinRegistry.useMonth(Month)
        PresentationSkinRegistry.useMemo(Memo)
        PresentationSkinRegistry.useAi(Ai)
        PresentationSkinRegistry.useSettings(Settings)
        PresentationSkinRegistry.useNavigation(Navigation)
        DeckPresentationSkinRegistry.use(Deck)
        PresentationSkinRegistry.useOverlay(Overlay)
        PresentationSkinRegistry.usePicker(NativePickerSkin)
        PresentationSkinRegistry.useChrome(Chrome)
    }

    fun isDeckRenderer(renderer: DeckVisualRenderer?): Boolean = renderer === Deck

    private object Timeline : CompiledTimelineSkin {
        override val rendererId = "native/timeline"
        @androidx.compose.runtime.Composable
        override fun Render(context: TimelineSkinContext) = context.Native()
    }

    private object Month : MonthVisualRenderer {
        override val id = "native/month"
        @androidx.compose.runtime.Composable
        override fun Render(context: MonthVisualContext) = context.Native()
    }

    private object Memo : MemoVisualRenderer {
        override val id = "native/memo"
        @androidx.compose.runtime.Composable
        override fun Render(context: MemoVisualContext) = context.Native()
    }

    private object Ai : AiVisualRenderer {
        override val id = "native/ai"
        @androidx.compose.runtime.Composable
        override fun Render(context: AiVisualContext) = context.Native()
    }

    private object Settings : SettingsVisualRenderer {
        override val id = "native/settings"
        @androidx.compose.runtime.Composable
        override fun Render(context: SettingsVisualContext) = context.Native()
    }

    private object Navigation : NavigationVisualRenderer {
        override val id = "native/navigation"
        @androidx.compose.runtime.Composable
        override fun Render(context: NavigationVisualContext) = context.Native()
    }

    private object Deck : DeckVisualRenderer {
        override val id = "native/deck"
        @androidx.compose.runtime.Composable
        override fun Render(context: DeckVisualContext) = context.Native()
    }

    private object Overlay : OverlayVisualRenderer {
        override val id = "native/overlay"
        @androidx.compose.runtime.Composable
        override fun Render(context: OverlayVisualContext) = NativeOverlaySkin.Render(context)
    }

    private object Chrome : ChromeVisualRenderer {
        override val id = "native/chrome"
        @androidx.compose.runtime.Composable
        override fun Render(context: ChromeVisualContext) = NativeChromeSkin.Render(context)
    }
}
