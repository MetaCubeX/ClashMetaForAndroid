package nota.ui.switch

/**
 * Motion constants aligned to Flyme `com.meizu.common.widget.Switch` (meizu-common).
 *
 * Source: SpringAnimation + SpringForce — not a fixed tween duration.
 */
object SwitchAnimationSpec {
    /** ref: Switch.STIFFNESS_DEFAULT */
    const val SpringStiffness: Float = 570f

    /** ref: Switch.DAMPING_CHECK_ON_DEFAULT — animate toward checked */
    const val SpringDampingOn: Float = 0.81f

    /** ref: Switch.DAMPING_CHECK_OFF_DEFAULT — animate toward unchecked */
    const val SpringDampingOff: Float = 0.69f

    fun springDamping(checked: Boolean): Float =
        if (checked) SpringDampingOn else SpringDampingOff
}
