package com.github.kr328.clash.design.screen

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.github.kr328.clash.design.MainDesign
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.util.CountryCodeMapper
import com.github.kr328.clash.design.util.performHapticClick
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.oom_wg.purejoy.mlang.MLang
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.useful.RemoveBlocklist
import top.yukonga.miuix.kmp.icon.icons.useful.Update
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

sealed class ConnectionStatus {
    data object Disconnected : ConnectionStatus()
    data object Connecting : ConnectionStatus()
    data class Connected(val delay: Int = 0) : ConnectionStatus()
}

@androidx.compose.runtime.Stable
data class ConnectionButtonState(
    val status: ConnectionStatus,
    val enabled: Boolean,
    val label: String,
    val buttonColor: Color
)

@Composable
private fun ConnectionButtonCircle(
    enabled: Boolean,
    buttonColor: Color,
    isConnecting: Boolean,
    onTap: () -> Unit,
    label: String,
    clickToScale: Boolean
) {
    val ctx = LocalContext.current

    val animatedColor by animateColorAsState(
        targetValue = buttonColor,
        animationSpec = tween(durationMillis = 350),
        label = "buttonColor"
    )

    val blurAmount by animateFloatAsState(
        targetValue = if (isConnecting) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "blur"
    )

    val scale by animateFloatAsState(
        targetValue = if (clickToScale || isConnecting) 0.8f else 1f,
        animationSpec = tween(
            durationMillis = 400,
            easing = FastOutSlowInEasing
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .size(200.dp)
            .scale(scale)
            .semantics {
                stateDescription = label
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawIntoCanvas { canvas ->
                val radius = size.minDimension / 2.5f
                val blur = size.minDimension / 18f
                val paint = Paint().apply {
                    isAntiAlias = true
                    color = animatedColor.copy(alpha = 0.28f).toArgb()
                    maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
                }
                canvas.nativeCanvas.drawCircle(
                    size.width / 2f,
                    size.height / 2f,
                    radius,
                    paint
                )
            }
        }

        Box(
            modifier = Modifier
                .size(148.dp)
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White)
                .blur(blurAmount.dp)
                .clickable(
                    enabled = enabled,
                    onClick = {
                        ctx.performHapticClick()
                        onTap()
                    },
                    role = Role.Button,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.yume),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
private fun InfoCard(
    proxyName: String?,
    proxyIp: String?,
    upload: String,
    download: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val flagUrl = remember(proxyName) { CountryCodeMapper.extractFlagUrl(proxyName) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = MiuixIcons.Useful.RemoveBlocklist,
                        contentDescription = MLang.label_download,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = proxyName?.takeIf { it.isNotBlank() } ?: MLang.status_no_node_selected,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = upload,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Icon(
                        imageVector = MiuixIcons.Useful.Update,
                        contentDescription = MLang.label_upload,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 使用网络加载的SVG国旗图标
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(flagUrl)
                            .decoderFactory(SvgDecoder.Factory())
                            .crossfade(true)
                            .build(),
                        contentDescription = MLang.label_country_flag,
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                    )
                    Text(
                        text = proxyIp?.takeIf { it.isNotBlank() } ?: MLang.label_unknown_protocol,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = download,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Icon(
                        imageVector = MiuixIcons.Useful.Update,
                        contentDescription = MLang.label_download,
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer(rotationZ = 180f),
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimatedConnectionText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier
) {
    // 文字淡入淡出动画 - 加快速度
    var currentText by remember { mutableStateOf(text) }
    var targetAlpha by remember { mutableStateOf(1f) }

    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = 250),
        label = "textAlpha"
    )

    LaunchedEffect(text) {
        if (text != currentText) {
            // 淡出
            targetAlpha = 0f
            delay(250)
            // 更新文字
            currentText = text
            // 淡入
            targetAlpha = 1f
        }
    }

    Text(
        text = currentText,
        style = style,
        textAlign = TextAlign.Center,
        modifier = modifier.graphicsLayer {
            this.alpha = alpha
        }
    )
}

@Composable
fun MainScreen(
    running: Boolean,
    currentProfile: String?,
    currentForwarded: String,
    currentUpload: String,
    currentDownload: String,
    currentMode: String,
    currentProxy: String?,
    currentProxySubtitle: String?,
    currentDelay: Int?,
    isToggling: Boolean,
    onRequest: (MainDesign.Request) -> Unit
) {
    // 点击触发的缩放动画状态
    var clickToScale by remember { mutableStateOf(false) }

    // 连接超时保护 - 如果 isToggling 超过 30 秒自动重置
    var toggleStartTime by remember { mutableStateOf(0L) }
    var actualToggling by remember { mutableStateOf(isToggling) }

    LaunchedEffect(isToggling) {
        if (isToggling && !actualToggling) {
            toggleStartTime = System.currentTimeMillis()
            actualToggling = true
        } else if (!isToggling) {
            actualToggling = false
            toggleStartTime = 0L
        }
    }

    // 超时检查和自动重置
    LaunchedEffect(actualToggling, toggleStartTime) {
        if (actualToggling && toggleStartTime > 0) {
            while (actualToggling && (System.currentTimeMillis() - toggleStartTime) < 30000) {
                kotlinx.coroutines.delay(1000)
            }
            // 30 秒后如果还在 toggling 状态，强制重置
            if (actualToggling) {
                actualToggling = false
                clickToScale = false
            }
        }
    }

    // 构建连接状态 - 改进判断逻辑
    val connectionStatus = remember(running, actualToggling, currentDelay) {
        when {
            actualToggling -> ConnectionStatus.Connecting
            running -> {
                val delay = currentDelay ?: 0
                if (currentProxy != null && delay <= 0) {
                    ConnectionStatus.Connected(delay)
                } else {
                    ConnectionStatus.Connected(delay)
                }
            }

            else -> ConnectionStatus.Disconnected
        }
    }

    // 构建按钮状态 - 优化连接成功判断
    val buttonState = remember(connectionStatus, actualToggling, currentProxy) {
        when (connectionStatus) {
            is ConnectionStatus.Connected -> {
                val hasProxy = !currentProxy.isNullOrBlank()
                ConnectionButtonState(
                    status = connectionStatus,
                    enabled = true,
                    label = when {
                        hasProxy -> MLang.status_connected
                        else -> MLang.status_connecting
                    },
                    buttonColor = when {
                        hasProxy -> Color(0xFF4CAF50)
                        else -> Color(0xFF6750A4)
                    }
                )
            }

            is ConnectionStatus.Connecting -> ConnectionButtonState(
                status = connectionStatus,
                enabled = false,
                label = MLang.status_connecting,
                buttonColor = Color(0xFF6750A4)
            )

            is ConnectionStatus.Disconnected -> ConnectionButtonState(
                status = connectionStatus,
                enabled = true,
                label = MLang.action_connect,
                buttonColor = Color(0xFFEF5350)
            )
        }
    }

    // 监听连接状态变化，自动清除点击缩放状态
    LaunchedEffect(actualToggling, running) {
        if (!actualToggling) {
            if (running) {
                kotlinx.coroutines.delay(100)
            }
            clickToScale = false
        }
    }

    // 只有在已连接状态（且不是连接中）时向上移动
    val isConnected = buttonState.status is ConnectionStatus.Connected
    val offsetY by animateDpAsState(
        targetValue = if (isConnected && !actualToggling) (-30).dp else 0.dp,
        animationSpec = spring(
            dampingRatio = 0.7f,
            stiffness = Spring.StiffnessLow
        ),
        label = "offsetY"
    )

    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()
    val hazeState = remember { HazeState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = MLang.app_name,
                scrollBehavior = scrollBehavior,
                modifier = Modifier.hazeEffect(state = hazeState)
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState)
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top,
                contentPadding = PaddingValues(
                    top = 135.dp,
                    bottom = 120.dp
                )
            ) {
                item(key = "connection_button") {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp)
                    ) {
                        Column(
                            modifier = Modifier.offset(y = offsetY),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            // 主连接按钮
                            ConnectionButtonCircle(
                                enabled = buttonState.enabled,
                                buttonColor = buttonState.buttonColor,
                                isConnecting = actualToggling,
                                onTap = {
                                    if (!running) {
                                        clickToScale = true
                                    }
                                    onRequest(MainDesign.Request.ToggleStatus)
                                },
                                label = buttonState.label,
                                clickToScale = clickToScale
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // 状态文字 - 缩小字体
                            AnimatedConnectionText(
                                text = buttonState.label,
                                style = MiuixTheme.textStyles.body1
                            )

                            // 配置名称 - 放大字体，始终显示
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = currentProfile ?: MLang.status_no_profile_selected,
                                style = MiuixTheme.textStyles.title3,
                                color = MiuixTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // 下半部分：信息卡片（只在连接时显示，淡入淡出动画）
                item(key = "info_card") {
                    AnimatedVisibility(
                        visible = running,
                        enter = fadeIn(animationSpec = tween(400)),
                        exit = fadeOut(animationSpec = tween(300))
                    ) {
                        InfoCard(
                            proxyName = currentProxy,
                            proxyIp = currentProxySubtitle,
                            upload = currentUpload,
                            download = currentDownload,
                            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 20.dp)
                        )
                    }
                }
            }
        }
    }
}

