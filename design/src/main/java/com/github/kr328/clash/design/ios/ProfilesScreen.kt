package com.github.kr328.clash.design.ios

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.ui.Insets
import com.github.kr328.clash.design.util.elapsedIntervalString
import com.github.kr328.clash.design.util.isShareableSubscriptionUrl
import com.github.kr328.clash.design.util.toBytesString
import com.github.kr328.clash.design.util.toDateStr
import com.github.kr328.clash.service.model.Profile
import java.util.Locale

@Composable
fun ProfilesScreen(
    insets: Insets,
    title: String,
    profiles: List<Profile>,
    loaded: Boolean,
    updatable: Boolean,
    allUpdating: Boolean,
    now: Long,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onUpdateAll: () -> Unit,
    onActive: (Profile) -> Unit,
    onUpdate: (Profile) -> Unit,
    onEdit: (Profile) -> Unit,
    onShare: (Profile) -> Unit,
    onDuplicate: (Profile) -> Unit,
    onDelete: (Profile) -> Unit,
) {
    var menu by remember { mutableStateOf<Profile?>(null) }
    IosScreenScaffold(
        insets = insets,
        title = title,
        onBack = onBack,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (allUpdating) {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = IosTheme.colors.blue,
                            strokeWidth = 2.dp,
                        )
                    }
                } else if (updatable) {
                    BarIcon(
                        image = Icons.Filled.Refresh,
                        description = stringResource(R.string.update_all),
                        onClick = onUpdateAll,
                    )
                }
                BarIcon(
                    image = Icons.Filled.Add,
                    description = stringResource(R.string._new),
                    onClick = onCreate,
                )
            }
        },
    ) {
        when {
            !loaded && profiles.isEmpty() -> IosProgressRow(stringResource(R.string.loading))
            profiles.isEmpty() -> IosEmptyState(
                message = stringResource(R.string.list_empty),
                action = stringResource(R.string._new),
                onAction = onCreate,
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
            ) {
                itemsIndexed(profiles, key = { _, profile -> profile.uuid }) { index, profile ->
                    ProfileRow(
                        profile = profile,
                        now = now,
                        showDivider = index < profiles.lastIndex,
                        onActive = { onActive(profile) },
                        onUpdate = { onUpdate(profile) },
                        onMore = { menu = profile },
                    )
                }
            }
        }
        menu?.let { profile ->
            IosActionSheet(
                actions = profileActions(
                    profile = profile,
                    onEdit = onEdit,
                    onShare = onShare,
                    onDuplicate = onDuplicate,
                    onDelete = onDelete,
                ),
                onDismiss = { menu = null },
                cancelLabel = stringResource(R.string.cancel),
                title = profile.name,
            )
        }
    }
}

@Composable
private fun ProfileRow(
    profile: Profile,
    now: Long,
    showDivider: Boolean,
    onActive: () -> Unit,
    onUpdate: () -> Unit,
    onMore: () -> Unit,
) {
    val fraction = usageFraction(profile)
    val canUpdate = profile.imported && profile.type != Profile.Type.File
    val updateIcon = rememberVectorPainter(Icons.Filled.Refresh)
    val updateLabel = stringResource(R.string.update)
    val detail = IosDetail(
        title = profile.name,
        subtitleAnnotated = profileSubtitle(profile, now, fraction),
        selected = profile.active,
        disclosure = false,
        onClick = onActive,
    )
    IosSideActionRow(
        row = detail,
        actionIcon = painterResource(R.drawable.ic_baseline_more_vert),
        actionDescription = stringResource(R.string.more),
        onAction = onMore,
        showDivider = showDivider,
        beforeAction = if (!canUpdate) {
            null
        } else {
            {
                IosNavIcon(
                    icon = updateIcon,
                    description = updateLabel,
                    onClick = onUpdate,
                )
            }
        },
        below = if (fraction == null) {
            null
        } else {
            {
                IosFractionBar(
                    fraction = fraction,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                )
            }
        },
    )
}

@Composable
private fun profileActions(
    profile: Profile,
    onEdit: (Profile) -> Unit,
    onShare: (Profile) -> Unit,
    onDuplicate: (Profile) -> Unit,
    onDelete: (Profile) -> Unit,
): List<IosAction> {
    val edit = stringResource(R.string.edit)
    val share = stringResource(R.string.share_subscription_qr)
    val duplicate = stringResource(R.string.duplicate)
    val delete = stringResource(R.string.delete)
    return buildList {
        add(IosAction(edit) { onEdit(profile) })
        if (isShareableSubscriptionUrl(profile.source)) {
            add(IosAction(share) { onShare(profile) })
        }
        if (profile.imported) {
            add(IosAction(duplicate) { onDuplicate(profile) })
        }
        add(IosAction(delete, destructive = true) { onDelete(profile) })
    }
}

@Composable
private fun profileSubtitle(profile: Profile, now: Long, fraction: Float?): AnnotatedString {
    val context = LocalContext.current
    val expiredColor = IosTheme.colors.red
    val typeName = when (profile.type) {
        Profile.Type.File -> stringResource(R.string.file)
        Profile.Type.Url -> stringResource(R.string.url)
        Profile.Type.External -> stringResource(R.string.external)
    }
    val typeLine = when {
        profile.pending -> stringResource(R.string.format_type_unsaved, typeName)
        profile.type == Profile.Type.Url -> null
        else -> typeName
    }
    val expiredLabel = stringResource(R.string.profile_expired)
    val expire = expireLine(profile, now, expiredLabel)
    val expired = profile.expire > 0L && profile.expire < now
    val updated = if (profile.expire > 0L || profile.updatedAt <= 0L) {
        null
    } else {
        (now - profile.updatedAt).elapsedIntervalString(context)
    }
    val lines = listOfNotNull(typeLine, trafficLine(profile, fraction), expire, updated)
    return buildAnnotatedString {
        lines.forEachIndexed { index, line ->
            if (index > 0) append('\n')
            if (expired && line == expire) {
                withStyle(SpanStyle(color = expiredColor)) { append(line) }
            } else {
                append(line)
            }
        }
    }
}

private fun trafficLine(profile: Profile, fraction: Float?): String? {
    val used = (profile.download + profile.upload).toBytesString()
    if (fraction != null) {
        return used + "/" + profile.total.toBytesString() + " · " + percentText(fraction)
    }
    if (profile.download < 2) return null
    return used
}

private fun percentText(fraction: Float): String {
    val pct = (fraction * 100f).coerceIn(0f, 100f)
    val number = if (pct > 0f && pct < 10f) {
        String.format(Locale.US, "%.1f", pct)
    } else {
        String.format(Locale.US, "%.0f", pct)
    }
    return "$number%"
}

private fun expireLine(profile: Profile, now: Long, expiredLabel: String): String? {
    if (profile.expire <= 0L) return null
    val date = profile.expire.toDateStr()
    return if (profile.expire < now) "$expiredLabel $date" else date
}

/**
 * Old binding: max 1000, integer (download + upload) / (total / 1000).
 * total/1000 == 0 throws. Skip the bar in that case.
 */
private fun usageFraction(profile: Profile): Float? {
    if (profile.total < 2) return null
    val divisor = profile.total / 1000
    if (divisor == 0L) return null
    val ticks = (profile.download + profile.upload) / divisor
    return (ticks.toFloat() / 1000f).coerceIn(0f, 1f)
}

@Composable
private fun BarIcon(
    image: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(44.dp)
            .semantics { role = Role.Button }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = image,
            contentDescription = description,
            tint = IosTheme.colors.blue,
            modifier = Modifier.size(22.dp),
        )
    }
}
