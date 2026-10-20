@file:Suppress("FunctionName")

package universe.agentic.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Cross-client single-line search / filter chrome ([BasicTextField]).
 *
 * Public API is brand-neutral — no `Singular*` / `Singularity*` symbols.
 * Product icons enter via [leadingIcon]; the library does not bind product resources.
 */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    variant: SearchFieldVariant = SearchFieldVariant.Filled,
    dimens: SearchFieldDimens = SearchFieldDimens.Default,
    showSearchIcon: Boolean = true,
    showClearButton: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    rowHeight: Dp? = null,
    focusRequester: FocusRequester? = null,
    imeAction: ImeAction = ImeAction.Search,
    onImeSearch: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    enabled: Boolean = true,
    textStyle: TextStyle? = null,
    containerColor: Color = Color(0xFFE5E5EA),
    contentColor: Color = Color.Black,
    placeholderColor: Color = Color(0x993C3C43),
    cursorColor: Color = Color(0xFF007AFF),
    clearTint: Color = Color(0x993C3C43),
    clearContentDescription: String = "Clear",
    testTag: String? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
) {
    val resolvedTextStyle = textStyle ?: TextStyle(fontSize = 17.sp, lineHeight = 22.sp)
    val fieldShape = RoundedCornerShape(dimens.cornerPopup.dp)
    val showClear = showClearButton && value.isNotEmpty()
    val minHeight = rowHeight ?: dimens.barHeightDefault.dp

    val variantChrome =
        when {
            hasFilledOuterMargin(variant) -> {
                Modifier
                    .padding(horizontal = dimens.spaceM.dp, vertical = dimens.spaceS.dp)
                    .clip(fieldShape)
                    .background(containerColor)
            }
            usesFilledBackground(variant) -> {
                Modifier
                    .clip(fieldShape)
                    .background(containerColor)
            }
            else -> Modifier
        }

    val fieldModifier =
        modifier
            .fillMaxWidth()
            .then(variantChrome)
            .height(minHeight)
            .padding(
                start = dimens.spaceM.dp,
                end = innerEndPadding(showClear, dimens.spaceXs, dimens.spaceM).dp,
            )

    Row(
        modifier = fieldModifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showSearchIcon) {
            if (leadingIcon != null) {
                leadingIcon()
            } else {
                Box(modifier = Modifier.size(dimens.iconSize.dp))
            }
        }
        val textPadding =
            if (showSearchIcon) {
                Modifier.padding(horizontal = dimens.spaceXs.dp)
            } else {
                Modifier.padding(vertical = dimens.spaceXs.dp)
            }
        val textFieldModifier =
            Modifier
                .weight(1f)
                .then(textPadding)
                .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .then(
                    if (onFocusChanged != null) {
                        Modifier.onFocusChanged { focusState ->
                            onFocusChanged(focusState.isFocused)
                        }
                    } else {
                        Modifier
                    },
                )

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = resolvedTextStyle.copy(color = contentColor),
            cursorBrush = SolidColor(cursorColor),
            keyboardOptions = KeyboardOptions(imeAction = imeAction),
            keyboardActions =
                KeyboardActions(
                    onSearch = { onImeSearch?.invoke() },
                ),
            modifier = textFieldModifier,
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        BasicText(
                            text = placeholder,
                            style = resolvedTextStyle.copy(color = placeholderColor),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
            },
        )
        if (showClear) {
            val effectiveRowHeight = (rowHeight ?: dimens.barHeightDefault.dp).value
            val clearSlot = resolveClearSlot(dimens, effectiveRowHeight)
            SearchFieldClearButton(
                onClick = { onValueChange("") },
                enabled = enabled,
                tint = clearTint,
                contentDescription = clearContentDescription,
                layoutWidth = clearSlot.layoutWidth.dp,
                visualHeight = clearSlot.layoutHeight.dp,
                touchTargetSize = clearSlot.touchSize.dp,
                iconSize = dimens.iconSize.dp,
            )
            val trailingGap =
                resolveClearTrailingGap(
                    showClear = true,
                    hasTrailing = trailing != null,
                    spaceXs = dimens.spaceXs,
                )
            if (trailingGap > 0f) {
                Spacer(modifier = Modifier.width(trailingGap.dp))
            }
        }
        if (trailing != null) {
            trailing()
        }
    }
}
