package nota.ui.switch

import nota.ui.theme.ShellCorner
import nota.ui.theme.ShellSpacing

/**
 * Track and thumb dimension SSOT.
 *
 * ref: Token table — track 22dp, on thumb 14dp, off thumb 4dp, corners Regular/Mini/ExtraMini.
 * trackWidth: Widget.MeizuCommon.Switch `mcSwitchMinWidth` = 40dip (values.xml);
 * Switch.java onMeasure → max(minWidth, 2×thumbToWidth + insets) = max(40, 36) = 40dp.
 */
data class SwitchTrackSpec(
    val trackHeight: Float = 22f,
    /** mcSwitchMinWidth (values.xml) / Switch.java onMeasure */
    val trackWidth: Float = 40f,
    /** mz_switch_thumb_to_height (values.xml) */
    val thumbOnHeight: Float = 14f,
    /** mz_switch_thumb_from_height (values.xml) — only read when [thumbMorphEnabled]. */
    val thumbOffHeight: Float = 4f,
    /** mz_switch_thumb_to_width (values.xml) */
    val thumbOnWidth: Float = 14f,
    /** mz_switch_thumb_from_width (values.xml) — only read when [thumbMorphEnabled]. */
    val thumbOffWidth: Float = 9f,
    /** mz_switch_thumb_to_corner_radius → fd_sys_radius_corner_smooth_mini */
    val thumbOnCorner: Float = ShellCorner.Mini,
    /** mz_switch_thumb_from_corner_radius → fd_sys_radius_corner_smooth_extra_mini */
    val thumbOffCorner: Float = ShellCorner.ExtraMini,
    /**
     * Interpolate the thumb between the `from_*` and `to_*` dimens while the track animates.
     *
     * Defaults to the AAR 8.1.0 behaviour, which is **no morph**: `Switch.java:286-291` builds
     * `mParams` once from `mz_switch_thumb_to_height/_to_width/_to_corner_radius` and
     * `setThumbPosition` (`Switch.java:669-693`) re-applies those same constants every frame, so
     * only x and the track colorFilter move. The three `mz_switch_thumb_from_*` dimens are
     * zero-referenced across `res/` and `sources/`.
     *
     * ref: rejected origin — `flyme/F11 控件 token 整理-OCR.md` ("开关关闭状态：内圆高度 4dp，圆角
     * 4dp") does specify the morph; set this to true to follow the design token sheet instead.
     */
    val thumbMorphEnabled: Boolean = false,
    /**
     * mc_switch_anim_track.xml `<shape><size height=22dp/><corners radius=11dp/></shape>` — a plain
     * `GradientDrawable` capsule, exactly half the track height.
     *
     * ref: rejected origin — the OCR token sheet says "圆角大于 11dp 即可，这里使用 cornerRegular
     * 12dp"; `GradientDrawable` would clamp 12dp back down to the 11dp capsule anyway.
     */
    val trackCorner: Float = 11f,
    /** mz_switch_thumb_padding_left / right (values.xml) */
    val horizontalInset: Float = ShellSpacing.Mini,
    /**
     * Library-own accessibility floor (dp) for the control box; **not** Flyme parity, so it is off
     * by default. Flyme measures the switch at `mSwitchWidth × mSwitchHeight` = 40dp × 22dp
     * (`Switch.java:481-483`) with no square expansion.
     */
    val minTouchTarget: Float = 0f,
)
