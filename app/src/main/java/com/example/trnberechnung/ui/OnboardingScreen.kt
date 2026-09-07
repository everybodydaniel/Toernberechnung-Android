package com.example.trnberechnung.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trnberechnung.R
import com.example.trnberechnung.ui.theme.OnboardingBlue
import com.example.trnberechnung.ui.theme.OnboardingCard
import com.example.trnberechnung.ui.theme.OnboardingInk
import com.example.trnberechnung.ui.theme.OnboardingMuted
import com.example.trnberechnung.ui.theme.OnboardingOrange
import com.example.trnberechnung.ui.theme.OnboardingTeal
import kotlin.math.atan2
import kotlinx.coroutines.launch

private const val ONBOARDING_PAGES = 3
private val OnboardingBackground = Color(0xFFEAFBFA)
private val OnboardingBrandBlue = Color(0xFF28549B)
private val TideNodeNavy = Color(0xFF062E4F)
private val TideNodeCyan = Color(0xFF12C5C7)
private val TideNodeSailWhite = Color(0xFFE5F8F7)
private val IllustrationCardShape = RoundedCornerShape(38.dp)

/** Onboarding-only tablet values. Compact dimensions stay at their existing call sites. */
private object TabletOnboardingTokens {
    val ScreenVerticalPadding = 20.dp
    val LargeScreenVerticalPadding = 22.dp
    val BrandSize = 52.dp
    val LargeBrandSize = 56.dp
    val BrandCornerRadius = 14.dp
    val LargeBrandCornerRadius = 15.dp
    val BrandGap = 12.dp
    val BrandFontSize = 35.sp
    val LargeBrandFontSize = 36.sp
    val BrandLineHeight = 42.sp
    val LargeBrandLineHeight = 43.sp
    val IllustrationMaxWidth = TabletLayoutTokens.CompactContentMaxWidth
    val IllustrationCardShape = RoundedCornerShape(44.dp)
    val IllustrationShadow = 10.dp
    val IllustrationPadding = TabletLayoutTokens.CardPadding
    val IllustrationInnerCornerRadius = 36.dp
    val IllustrationMaxHeight = 360.dp
    val IllustrationDesignSize = DpSize(width = 672.dp, height = 342.dp)
    val BodyMaxWidth = TabletLayoutTokens.OverlayMaxWidth
    val EyebrowFontSize = 19.sp
    val TitleFontSize = 37.sp
    val LargeTitleFontSize = 38.sp
    val TitleLineHeight = 43.sp
    val LargeTitleLineHeight = 44.sp
    val BodyFontSize = 22.sp
    val BodyLineHeight = 30.sp
    val ButtonFontSize = 21.sp
    val PageTopSpacing = 18.dp
    val IllustrationTextSpacing = 24.dp
    val EyebrowTitleSpacing = 12.dp
    val TitleBodySpacing = 16.dp
    val DisclaimerSpacing = 24.dp
    val PageBottomPadding = 28.dp
    val HeaderPagerSpacing = 20.dp
    val IndicatorButtonSpacing = 22.dp
    val FooterBottomSpacing = 12.dp
    val ActiveDotWidth = 36.dp
    val InactiveDotWidth = 14.dp
    val DotHeight = 14.dp
    val DotSpacing = 11.dp
    val DisclaimerShape = RoundedCornerShape(26.dp)
    val DisclaimerPadding = 16.dp
    val DisclaimerFontSize = 16.sp
    val DisclaimerLineHeight = 22.sp
    val DisclaimerBorderWidth = 2.dp
    val DisclaimerTextTopPadding = 10.dp
    val DisclaimerTextEndPadding = 6.dp
    const val RouteBoatBob = 6f
    const val RouteWaveOffset = 10f
    val WeatherContentPadding = 24.dp
    val WeatherBottomPadding = 6.dp
    val WeatherLocationIconSize = 30.dp
    val WeatherLocationGap = 14.dp
    val WeatherLocationFontSize = 30.sp
    val WeatherSecondaryFontSize = 16.sp
    val WeatherSunFontSize = 53.sp
    val WeatherHeaderGap = 18.dp
    val WeatherTemperatureFontSize = 72.sp
    val WeatherTemperatureGap = 16.dp
    val WeatherConditionBottomPadding = 9.dp
    val WeatherConditionFontSize = 28.sp
    val WeatherHourlyGap = 10.dp
    val WeatherHourlyShape = RoundedCornerShape(26.dp)
    val WeatherHourlyVerticalPadding = 9.dp
    val WeatherHourFontSize = 13.sp
    val WeatherGlyphVerticalPadding = 5.dp
    val WeatherGlyphHeight = 34.dp
    val WeatherGlyphFontSize = 28.sp
    val WeatherHourlyTemperatureFontSize = 19.sp
    val WeatherWindGap = 7.dp
    val WeatherWindFontSize = 15.sp
    const val CrewAvatarBob = 5f
    const val CrewDashDistance = 72f
    val CrewAvatarAreaHeight = 190.dp
    val CrewSectionSpacing = 22.dp
    val CrewFeatureSpacing = 12.dp
    val CrewFeatureIconSize = 34.dp
    val CrewConnectionEndpointInset = 48.dp
    val CrewConnectionStrokeWidth = 2.5.dp
    val CrewConnectionDashLength = 8.5.dp
    val CrewConnectionDashGap = 6.dp
    val CrewMessageFontSize = 18.sp
    val CrewMessageShape = RoundedCornerShape(24.dp)
    val CrewMessageHorizontalPadding = 17.dp
    val CrewMessageVerticalPadding = 12.dp
    val CrewMessageAvatarSpacing = 12.dp
    val CrewAvatarSize = 96.dp
    val CrewAvatarShape = RoundedCornerShape(48.dp)
    val CrewAvatarIconSize = 50.dp
    val CrewFeatureHeight = 100.dp
    val CrewFeatureShape = RoundedCornerShape(24.dp)
    val CrewFeatureLabelSpacing = 6.dp
    val CrewFeatureLabelFontSize = 15.sp
    const val IllustrationAspectRatio = 2f
    const val IllustrationHeightFraction = 0.30f
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onCompleted: () -> Unit) {
    val adaptiveLayout = currentAdaptiveLayout()
    val pagerState = rememberPagerState(pageCount = { ONBOARDING_PAGES })
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(OnboardingState()) }
    LaunchedEffect(pagerState.currentPage) { state = state.copy(page = pagerState.currentPage) }

    Box(Modifier.fillMaxSize().background(OnboardingBackground).testTag("onboarding_screen")) {
        val screenModifier =
            if (adaptiveLayout.isTablet) {
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(
                        horizontal = adaptiveLayout.horizontalScreenPadding,
                        vertical =
                            if (adaptiveLayout.isLargeTablet) {
                                TabletOnboardingTokens.LargeScreenVerticalPadding
                            } else {
                                TabletOnboardingTokens.ScreenVerticalPadding
                            },
                    )
            } else {
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(horizontal = 24.dp, vertical = 18.dp)
            }
        Column(
            // Keep the content out of status/navigation bars and display cut-outs while the
            // background continues behind them as a calm, light header area.
            modifier = screenModifier,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OnboardingBrand(adaptiveLayout)
            Spacer(
                Modifier.height(
                    if (adaptiveLayout.isTablet) {
                        TabletOnboardingTokens.HeaderPagerSpacing
                    } else {
                        16.dp
                    },
                ),
            )
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                OnboardingPage(
                    page = page,
                    disclaimerAccepted = state.disclaimerAccepted,
                    adaptiveLayout = adaptiveLayout,
                ) {
                    state = state.copy(disclaimerAccepted = it)
                }
            }
            PageIndicator(pagerState.currentPage, adaptiveLayout)
            Spacer(
                Modifier.height(
                    if (adaptiveLayout.isTablet) {
                        TabletOnboardingTokens.IndicatorButtonSpacing
                    } else {
                        18.dp
                    },
                ),
            )
            val accent = pageAccent(pagerState.currentPage)
            val buttonHeight =
                if (adaptiveLayout.isLargeTablet) {
                    TabletLayoutTokens.LargePrimaryControlHeight
                } else if (adaptiveLayout.isTablet) {
                    TabletLayoutTokens.PrimaryControlHeight
                } else {
                    60.dp
                }
            val buttonModifier =
                if (adaptiveLayout.isTablet) {
                    Modifier
                        .widthIn(max = adaptiveLayout.overlayMaxWidth)
                        .fillMaxWidth()
                        .height(buttonHeight)
                        .testTag("onboarding_continue")
                } else {
                    Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .testTag("onboarding_continue")
                }
            Button(
                onClick = {
                    if (pagerState.currentPage == OnboardingState.LAST_PAGE) {
                        if (state.canFinish) onCompleted()
                    } else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                },
                enabled = pagerState.currentPage != OnboardingState.LAST_PAGE || state.canFinish,
                modifier = buttonModifier,
                shape = RoundedCornerShape(if (adaptiveLayout.isTablet) buttonHeight / 2 else 30.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White, disabledContainerColor = accent.copy(alpha = .35f), disabledContentColor = Color.White.copy(alpha = .75f))
            ) {
                Text(
                    stringResource(
                        if (pagerState.currentPage == OnboardingState.LAST_PAGE) {
                            R.string.onboarding_start
                        } else {
                            R.string.onboarding_continue
                        },
                    ),
                    fontSize =
                        if (adaptiveLayout.isTablet) {
                            TabletOnboardingTokens.ButtonFontSize
                        } else {
                            18.sp
                        },
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (pagerState.currentPage == OnboardingState.LAST_PAGE) {
                        Icons.Default.Check
                    } else {
                        Icons.AutoMirrored.Filled.ArrowForward
                    },
                    null,
                    modifier =
                        if (adaptiveLayout.isTablet) {
                            Modifier.size(TabletLayoutTokens.StandardIconSize)
                        } else {
                            Modifier
                        },
                )
            }
            Spacer(
                Modifier.height(
                    if (adaptiveLayout.isTablet) {
                        TabletOnboardingTokens.FooterBottomSpacing
                    } else {
                        10.dp
                    },
                ),
            )
        }
    }
}

@Composable
private fun OnboardingBrand(adaptiveLayout: AdaptiveLayout) =
    Row(
        modifier =
            if (adaptiveLayout.isTablet) {
                Modifier
                    .widthIn(max = adaptiveLayout.mainContentMaxWidth)
                    .fillMaxWidth()
            } else {
                Modifier.fillMaxWidth()
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.Image(
            painter = painterResource(R.drawable.tidenode_mark),
            contentDescription = "TideNode Logo",
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier =
                if (adaptiveLayout.isTablet) {
                    Modifier
                        .size(
                            if (adaptiveLayout.isLargeTablet) {
                                TabletOnboardingTokens.LargeBrandSize
                            } else {
                                TabletOnboardingTokens.BrandSize
                            },
                        )
                        .clip(
                            RoundedCornerShape(
                                if (adaptiveLayout.isLargeTablet) {
                                    TabletOnboardingTokens.LargeBrandCornerRadius
                                } else {
                                    TabletOnboardingTokens.BrandCornerRadius
                                },
                            ),
                        )
                } else {
                    Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                },
        )
        Spacer(
            Modifier.width(
                if (adaptiveLayout.isTablet) {
                    TabletOnboardingTokens.BrandGap
                } else {
                    10.dp
                },
            ),
        )
        Text(
            text = stringResource(R.string.onboarding_brand),
            color = TideNodeNavy,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize =
                    if (adaptiveLayout.isLargeTablet) {
                        TabletOnboardingTokens.LargeBrandFontSize
                    } else if (adaptiveLayout.isTablet) {
                        TabletOnboardingTokens.BrandFontSize
                    } else {
                        29.sp
                    },
                lineHeight =
                    if (adaptiveLayout.isLargeTablet) {
                        TabletOnboardingTokens.LargeBrandLineHeight
                    } else if (adaptiveLayout.isTablet) {
                        TabletOnboardingTokens.BrandLineHeight
                    } else {
                        34.sp
                    },
                letterSpacing = if (adaptiveLayout.isTablet) (-0.5).sp else (-0.4).sp,
            ),
        )
    }

@Composable
private fun OnboardingPage(
    page: Int,
    disclaimerAccepted: Boolean,
    adaptiveLayout: AdaptiveLayout,
    onDisclaimerChanged: (Boolean) -> Unit,
) {
    val (eyebrow, title, body) = when (page) {
        0 -> Triple(R.string.onboarding_navigation_eyebrow, R.string.onboarding_navigation_title, R.string.onboarding_navigation_body)
        1 -> Triple(R.string.onboarding_weather_eyebrow, R.string.onboarding_weather_title, R.string.onboarding_weather_body)
        else -> Triple(R.string.onboarding_crew_eyebrow, R.string.onboarding_crew_title, R.string.onboarding_crew_body)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                bottom =
                    if (adaptiveLayout.isTablet) {
                        TabletOnboardingTokens.PageBottomPadding
                    } else {
                        24.dp
                    },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(
            Modifier.height(
                if (adaptiveLayout.isTablet) {
                    TabletOnboardingTokens.PageTopSpacing
                } else {
                    14.dp
                },
            ),
        )
        IllustrationCard(page, adaptiveLayout)
        Spacer(
            Modifier.height(
                if (adaptiveLayout.isTablet) {
                    TabletOnboardingTokens.IllustrationTextSpacing
                } else {
                    20.dp
                },
            ),
        )
        Text(
            stringResource(eyebrow),
            color = pageAccent(page),
            style = MaterialTheme.typography.labelLarge,
            fontSize =
                if (adaptiveLayout.isTablet) {
                    TabletOnboardingTokens.EyebrowFontSize
                } else {
                    16.sp
                },
            textAlign = TextAlign.Center,
        )
        Spacer(
            Modifier.height(
                if (adaptiveLayout.isTablet) {
                    TabletOnboardingTokens.EyebrowTitleSpacing
                } else {
                    10.dp
                },
            ),
        )
        Text(
            stringResource(title),
            color = OnboardingInk,
            fontSize =
                if (adaptiveLayout.isLargeTablet) {
                    TabletOnboardingTokens.LargeTitleFontSize
                } else if (adaptiveLayout.isTablet) {
                    TabletOnboardingTokens.TitleFontSize
                } else {
                    31.sp
                },
            lineHeight =
                if (adaptiveLayout.isLargeTablet) {
                    TabletOnboardingTokens.LargeTitleLineHeight
                } else if (adaptiveLayout.isTablet) {
                    TabletOnboardingTokens.TitleLineHeight
                } else {
                    36.sp
                },
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
        )
        Spacer(
            Modifier.height(
                if (adaptiveLayout.isTablet) {
                    TabletOnboardingTokens.TitleBodySpacing
                } else {
                    14.dp
                },
            ),
        )
        Text(
            stringResource(body),
            color = OnboardingMuted,
            fontSize =
                if (adaptiveLayout.isTablet) {
                    TabletOnboardingTokens.BodyFontSize
                } else {
                    18.sp
                },
            lineHeight =
                if (adaptiveLayout.isTablet) {
                    TabletOnboardingTokens.BodyLineHeight
                } else {
                    25.sp
                },
            textAlign = TextAlign.Center,
            modifier =
                if (adaptiveLayout.isTablet) {
                    Modifier.widthIn(max = TabletOnboardingTokens.BodyMaxWidth)
                } else {
                    Modifier.widthIn(max = 560.dp)
                },
        )
        if (page == OnboardingState.LAST_PAGE) {
            Spacer(
                Modifier.height(
                    if (adaptiveLayout.isTablet) {
                        TabletOnboardingTokens.DisclaimerSpacing
                    } else {
                        20.dp
                    },
                ),
            )
            Disclaimer(disclaimerAccepted, adaptiveLayout, onDisclaimerChanged)
        }
    }
}

@Composable
private fun IllustrationCard(
    page: Int,
    adaptiveLayout: AdaptiveLayout,
) {
    val shape =
        if (adaptiveLayout.isTablet) {
            TabletOnboardingTokens.IllustrationCardShape
        } else {
            IllustrationCardShape
        }
    val sizeModifier =
        if (adaptiveLayout.isTablet) {
            val illustrationWidth = tabletIllustrationWidth(adaptiveLayout)
            Modifier
                .width(illustrationWidth)
                .aspectRatio(TabletOnboardingTokens.IllustrationAspectRatio)
        } else {
            Modifier
                .fillMaxWidth()
                .height(if (page == OnboardingState.LAST_PAGE) 310.dp else 340.dp)
                .padding(horizontal = 10.dp)
        }
    Box(
        modifier = sizeModifier
            .shadow(
                elevation =
                    if (adaptiveLayout.isTablet) {
                        TabletOnboardingTokens.IllustrationShadow
                    } else {
                        8.dp
                    },
                shape = shape,
                clip = false,
                ambientColor = TideNodeNavy.copy(alpha = .06f),
                spotColor = TideNodeNavy.copy(alpha = .10f),
            )
            .clip(shape)
            .background(OnboardingCard)
            .padding(
                if (adaptiveLayout.isTablet) {
                    TabletOnboardingTokens.IllustrationPadding
                } else {
                    18.dp
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (adaptiveLayout.isTablet) {
            TabletIllustration(page)
        } else {
            when (page) {
                0 -> RouteIllustration(isTablet = false)
                1 -> WeatherIllustration(isTablet = false)
                else -> CrewIllustration(isTablet = false)
            }
        }
    }
}

private fun tabletIllustrationWidth(adaptiveLayout: AdaptiveLayout) =
    with(TabletOnboardingTokens) {
        val availableWidth =
            (adaptiveLayout.availableWidthDp.dp - adaptiveLayout.horizontalScreenPadding * 2)
                .coerceAtLeast(0.dp)
        val heightLimit =
            (adaptiveLayout.availableHeightDp.dp * IllustrationHeightFraction)
                .coerceAtMost(IllustrationMaxHeight)
        minOf(
            availableWidth,
            IllustrationMaxWidth,
            heightLimit * IllustrationAspectRatio,
        )
    }

@Composable
private fun TabletIllustration(page: Int) {
    val designSize = TabletOnboardingTokens.IllustrationDesignSize
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val scale =
            minOf(
                maxWidth.value / designSize.width.value,
                maxHeight.value / designSize.height.value,
            )
        Box(
            modifier =
                Modifier
                    .requiredSize(designSize)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
        ) {
            when (page) {
                0 -> RouteIllustration(isTablet = true)
                1 -> WeatherIllustration(isTablet = true)
                else -> CrewIllustration(isTablet = true)
            }
        }
    }
}

@Composable
private fun RouteIllustration(isTablet: Boolean) {
    val transition = rememberInfiniteTransition(label = "route")
    val boatBob by transition.animateFloat(
        initialValue = if (isTablet) -TabletOnboardingTokens.RouteBoatBob else -5f,
        targetValue = if (isTablet) TabletOnboardingTokens.RouteBoatBob else 5f,
        animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Reverse),
        label = "boatBob"
    )
    // The boat sails the route instead of sitting at its end and bobbing. It eases away from the
    // start marker, arrives at the destination, holds there for a moment so the arrival reads as an
    // arrival, and then the passage begins again.
    val voyage by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 6800
                0f at 0 using FastOutSlowInEasing
                1f at 5200
                1f at 6800
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "voyage"
    )
    val waveOffset by transition.animateFloat(
        initialValue = if (isTablet) -TabletOnboardingTokens.RouteWaveOffset else -8f,
        targetValue = if (isTablet) TabletOnboardingTokens.RouteWaveOffset else 8f,
        animationSpec = infiniteRepeatable(tween(2600), RepeatMode.Reverse),
        label = "waveOffset"
    )

    val innerCornerRadius =
        if (isTablet) {
            TabletOnboardingTokens.IllustrationInnerCornerRadius
        } else {
            30.dp
        }
    Canvas(Modifier.fillMaxSize().clip(RoundedCornerShape(innerCornerRadius))) {
        val scale = size.minDimension
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Color(0xFF75C9E6), Color(0xFF287FA9))),
            cornerRadius =
                if (isTablet) {
                    val radius = innerCornerRadius.toPx()
                    CornerRadius(radius, radius)
                } else {
                    CornerRadius(30f, 30f)
                },
        )
        repeat(3) { index ->
            val top = size.height * (.47f + index * .15f)
            val waveDepth = scale * (.030f + index * .003f)
            val shift = if (index % 2 == 0) waveOffset else -waveOffset
            val path = Path().apply {
                moveTo(0f, top + shift * 0.3f)
                cubicTo(size.width * .22f + shift, top - waveDepth, size.width * .44f, top + waveDepth, size.width * .64f + shift, top + waveDepth * .14f)
                cubicTo(size.width * .82f, top - waveDepth * .74f, size.width, top + waveDepth * .54f + shift * 0.3f, size.width, top + waveDepth * .54f)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            val color = when (index) {
                0 -> Color(0xFF63B5D2).copy(alpha = .42f)
                1 -> Color(0xFF469FC2).copy(alpha = .48f)
                else -> Color(0xFF347FA5).copy(alpha = .42f)
            }
            drawPath(path, color)
        }

        val start = Offset(size.width * .14f, size.height * .70f)
        val destination = Offset(size.width * .83f, size.height * .33f)
        val markerRadius = scale * .043f
        val completeRoute = Path().apply {
            moveTo(start.x, start.y)
            cubicTo(
                size.width * .42f,
                size.height * .80f,
                size.width * .57f,
                size.height * .30f,
                destination.x,
                destination.y,
            )
        }
        val routeMeasure = androidx.compose.ui.graphics.PathMeasure().apply {
            setPath(completeRoute, forceClosed = false)
        }
        val routeStartDistance = markerRadius + 6.dp.toPx()
        // The boat sails the route to its very end - it comes to rest on the destination itself,
        // never short of it. The destination is drawn as a ring wide enough to hold the hull, so
        // arriving means mooring inside the mark rather than covering it up.
        val destinationRadius = scale * .088f
        val boatDistance =
            routeStartDistance + (routeMeasure.length - routeStartDistance) * voyage

        // Two strokes tell the story without a word: the wake behind the boat is solid, the water
        // still ahead of it stays dashed. Both stop at the ring, where the boat takes over.
        val strokeWidth = scale * .014f
        val routeEndDistance =
            (routeMeasure.length - destinationRadius).coerceAtLeast(routeStartDistance)
        val wakeEnd = boatDistance.coerceIn(routeStartDistance, routeEndDistance)
        val sailed = Path()
        routeMeasure.getSegment(
            startDistance = routeStartDistance,
            stopDistance = wakeEnd,
            destination = sailed,
            startWithMoveTo = true,
        )
        drawPath(
            path = sailed,
            color = Color.White.copy(alpha = .92f),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )
        val ahead = Path()
        routeMeasure.getSegment(
            startDistance = wakeEnd,
            stopDistance = routeEndDistance,
            destination = ahead,
            startWithMoveTo = true,
        )
        drawPath(
            path = ahead,
            color = Color.White.copy(alpha = .52f),
            style = Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                    floatArrayOf(scale * .034f, scale * .024f),
                    phase = 0f,
                ),
            ),
        )

        drawCircle(Color.White.copy(alpha = 0.35f), radius = markerRadius * 1.4f, center = start)
        drawCircle(Color.White, radius = markerRadius, center = start)
        val pinHeight = markerRadius * 1.24f
        val pinWidth = pinHeight * .68f
        val pinHalfWidth = pinWidth * .5f
        val pinHalfHeight = pinHeight * .5f
        val pin = Path().apply {
            moveTo(start.x, start.y + pinHalfHeight)
            cubicTo(
                start.x - pinWidth * .08f,
                start.y + pinHeight * .34f,
                start.x - pinHalfWidth,
                start.y + pinHeight * .13f,
                start.x - pinHalfWidth,
                start.y - pinHeight * .08f,
            )
            cubicTo(
                start.x - pinHalfWidth,
                start.y - pinHeight * .32f,
                start.x - pinWidth * .27f,
                start.y - pinHalfHeight,
                start.x,
                start.y - pinHalfHeight,
            )
            cubicTo(
                start.x + pinWidth * .27f,
                start.y - pinHalfHeight,
                start.x + pinHalfWidth,
                start.y - pinHeight * .32f,
                start.x + pinHalfWidth,
                start.y - pinHeight * .08f,
            )
            cubicTo(
                start.x + pinHalfWidth,
                start.y + pinHeight * .13f,
                start.x + pinWidth * .08f,
                start.y + pinHeight * .34f,
                start.x,
                start.y + pinHalfHeight,
            )
            close()
        }
        drawPath(pin, OnboardingBrandBlue)
        drawCircle(
            color = Color.White,
            radius = pinWidth * .13f,
            center = Offset(start.x, start.y - pinHeight * .13f),
        )

        // The destination: a mooring ring the arriving boat settles into. Wide enough that the hull
        // sits inside it, so the mark stays readable at the end of the passage.
        drawCircle(
            color = Color.White.copy(alpha = .22f),
            radius = destinationRadius,
            center = destination,
        )
        drawCircle(
            color = Color.White.copy(alpha = .92f),
            radius = destinationRadius,
            center = destination,
            style = Stroke(width = scale * .010f),
        )

        val boat = routeMeasure.getPosition(boatDistance)
        val tangent = routeMeasure.getTangent(boatDistance)
        // Damped so the hull leans into the curve instead of standing on its bow.
        val heading = Math.toDegrees(atan2(tangent.y, tangent.x).toDouble()).toFloat() * 0.55f
        val x = boat.x
        // The swell fades out as the boat comes in, so it settles at the destination instead of
        // bobbing on the spot during the hold at the end of the loop.
        val y = boat.y + boatBob * (1f - voyage)

        rotate(degrees = heading, pivot = Offset(x, y)) {
            drawPath(
                Path().apply {
                    moveTo(x + scale * .002f, y + scale * .023f)
                    lineTo(x + scale * .002f, y - scale * .075f)
                    cubicTo(x + scale * .023f, y - scale * .062f, x + scale * .042f, y - scale * .021f, x + scale * .047f, y + scale * .022f)
                    cubicTo(x + scale * .030f, y + scale * .019f, x + scale * .016f, y + scale * .020f, x + scale * .002f, y + scale * .023f)
                    close()
                },
                Color.White,
            )
            drawPath(
                Path().apply {
                    moveTo(x - scale * .005f, y + scale * .021f)
                    lineTo(x - scale * .005f, y - scale * .052f)
                    cubicTo(x - scale * .022f, y - scale * .042f, x - scale * .037f, y - scale * .012f, x - scale * .043f, y + scale * .021f)
                    close()
                },
                Color(0xFFE8F8FF),
            )
            drawPath(
                Path().apply {
                    moveTo(x - scale * .043f, y + scale * .029f)
                    quadraticTo(x, y + scale * .036f, x + scale * .047f, y + scale * .027f)
                    lineTo(x + scale * .022f, y + scale * .051f)
                    quadraticTo(x - scale * .002f, y + scale * .055f, x - scale * .029f, y + scale * .048f)
                    close()
                },
                Color.White,
            )
        }
    }
}

/**
 * The weather card. Only the sun moves - the temperature block and the hourly strip stay put, so
 * the card reads as a weather panel rather than something drifting on the page.
 */
@Composable
private fun WeatherIllustration(isTablet: Boolean) {
    val transition = rememberInfiniteTransition(label = "weather")
    val sunRotate by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(14000), RepeatMode.Restart),
        label = "sunRotate"
    )
    val sunPulse by transition.animateFloat(
        initialValue = 0.90f,
        targetValue = 1.10f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
        label = "sunPulse"
    )

    Box(
        Modifier
            .fillMaxSize()
            .clip(
                RoundedCornerShape(
                    if (isTablet) {
                        TabletOnboardingTokens.IllustrationInnerCornerRadius
                    } else {
                        30.dp
                    },
                ),
            )
            .background(Brush.linearGradient(listOf(Color(0xFF2398D7), Color(0xFF12396C)))),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(
                    start = if (isTablet) TabletOnboardingTokens.WeatherContentPadding else 22.dp,
                    top = if (isTablet) TabletOnboardingTokens.WeatherContentPadding else 22.dp,
                    end = if (isTablet) TabletOnboardingTokens.WeatherContentPadding else 22.dp,
                    bottom = if (isTablet) TabletOnboardingTokens.WeatherBottomPadding else 4.dp,
                ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.LocationOn,
                    null,
                    tint = Color.White,
                    modifier =
                        Modifier.size(
                            if (isTablet) {
                                TabletOnboardingTokens.WeatherLocationIconSize
                            } else {
                                26.dp
                            },
                        ),
                )
                Spacer(
                    Modifier.width(
                        if (isTablet) TabletOnboardingTokens.WeatherLocationGap else 12.dp,
                    ),
                )
                Column {
                    Text(
                        stringResource(R.string.onboarding_weather_location),
                        color = Color.White,
                        fontSize =
                            if (isTablet) {
                                TabletOnboardingTokens.WeatherLocationFontSize
                            } else {
                                26.sp
                            },
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        stringResource(R.string.onboarding_weather_now),
                        color = Color.White.copy(alpha = .72f),
                        fontSize =
                            if (isTablet) {
                                TabletOnboardingTokens.WeatherSecondaryFontSize
                            } else {
                                14.sp
                            },
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "☀",
                    color = Color(0xFFFFC400),
                    fontSize =
                        if (isTablet) {
                            TabletOnboardingTokens.WeatherSunFontSize
                        } else {
                            46.sp
                        },
                    modifier = Modifier
                        .scale(sunPulse)
                        .graphicsLayer(rotationZ = sunRotate)
                )
            }
            Spacer(
                Modifier.height(
                    if (isTablet) TabletOnboardingTokens.WeatherHeaderGap else 16.dp,
                ),
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    stringResource(R.string.onboarding_weather_temperature),
                    color = Color.White,
                    fontSize =
                        if (isTablet) {
                            TabletOnboardingTokens.WeatherTemperatureFontSize
                        } else {
                            62.sp
                        },
                    fontWeight = FontWeight.Light,
                )
                Spacer(
                    Modifier.width(
                        if (isTablet) TabletOnboardingTokens.WeatherTemperatureGap else 14.dp,
                    ),
                )
                Column(
                    Modifier.padding(
                        bottom =
                            if (isTablet) {
                                TabletOnboardingTokens.WeatherConditionBottomPadding
                            } else {
                                8.dp
                            },
                    ),
                ) {
                    Text(
                        stringResource(R.string.onboarding_weather_sunny),
                        color = Color.White,
                        fontSize =
                            if (isTablet) {
                                TabletOnboardingTokens.WeatherConditionFontSize
                            } else {
                                24.sp
                            },
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        stringResource(R.string.onboarding_weather_high_low),
                        color = Color.White.copy(alpha = .7f),
                        fontSize =
                            if (isTablet) {
                                TabletOnboardingTokens.WeatherSecondaryFontSize
                            } else {
                                14.sp
                            },
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(
                Modifier.height(
                    if (isTablet) TabletOnboardingTokens.WeatherHourlyGap else 8.dp,
                ),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(
                        if (isTablet) {
                            TabletOnboardingTokens.WeatherHourlyShape
                        } else {
                            RoundedCornerShape(22.dp)
                        },
                    )
                    .background(Color.White.copy(alpha = .16f))
                    .padding(
                        vertical =
                            if (isTablet) {
                                TabletOnboardingTokens.WeatherHourlyVerticalPadding
                            } else {
                                8.dp
                            },
                    ),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                listOf("Jetzt", "20:00", "21:00", "22:00").forEachIndexed { index, time ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            time,
                            color = Color.White.copy(alpha = .75f),
                            fontSize =
                                if (isTablet) {
                                    TabletOnboardingTokens.WeatherHourFontSize
                                } else {
                                    11.sp
                                },
                            fontWeight = FontWeight.Bold,
                        )
                        // The sun and moon glyphs have different heights, so letting them size
                        // their own row put them - and the temperatures underneath - on different
                        // baselines. A fixed box centres both on one line.
                        Box(
                            modifier = Modifier
                                .padding(
                                    vertical =
                                        if (isTablet) {
                                            TabletOnboardingTokens.WeatherGlyphVerticalPadding
                                        } else {
                                            4.dp
                                        },
                                )
                                .height(
                                    if (isTablet) {
                                        TabletOnboardingTokens.WeatherGlyphHeight
                                    } else {
                                        30.dp
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (index < 2) "☀" else "☾",
                                color = if (index < 2) Color(0xFFFFC400) else Color.White,
                                fontSize =
                                    if (isTablet) {
                                        TabletOnboardingTokens.WeatherGlyphFontSize
                                    } else {
                                        24.sp
                                    },
                                lineHeight =
                                    if (isTablet) {
                                        TabletOnboardingTokens.WeatherGlyphFontSize
                                    } else {
                                        24.sp
                                    },
                            )
                        }
                        Text(
                            "${21 - index}°",
                            color = Color.White,
                            fontSize =
                                if (isTablet) {
                                    TabletOnboardingTokens.WeatherHourlyTemperatureFontSize
                                } else {
                                    16.sp
                                },
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Spacer(
                Modifier.height(
                    if (isTablet) TabletOnboardingTokens.WeatherWindGap else 6.dp,
                ),
            )
            Text(
                stringResource(R.string.onboarding_weather_wind),
                color = Color.White.copy(alpha = .88f),
                fontSize =
                    if (isTablet) {
                        TabletOnboardingTokens.WeatherWindFontSize
                    } else {
                        13.sp
                    },
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CrewIllustration(isTablet: Boolean) {
    val transition = rememberInfiniteTransition(label = "crew")
    val avatarBob1 by transition.animateFloat(
        initialValue = if (isTablet) -TabletOnboardingTokens.CrewAvatarBob else -4f,
        targetValue = if (isTablet) TabletOnboardingTokens.CrewAvatarBob else 4f,
        animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Reverse),
        label = "avatarBob1"
    )
    val avatarBob2 by transition.animateFloat(
        initialValue = if (isTablet) TabletOnboardingTokens.CrewAvatarBob else 4f,
        targetValue = if (isTablet) -TabletOnboardingTokens.CrewAvatarBob else -4f,
        animationSpec = infiniteRepeatable(tween(2400), RepeatMode.Reverse),
        label = "avatarBob2"
    )
    val dashPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (isTablet) -TabletOnboardingTokens.CrewDashDistance else -60f,
        animationSpec = infiniteRepeatable(tween(2500), RepeatMode.Restart),
        label = "cDash"
    )

    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(
                    if (isTablet) TabletOnboardingTokens.CrewAvatarAreaHeight else 168.dp,
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            CrewConnectionLine(
                modifier = Modifier.fillMaxSize(),
                dashPhase = dashPhase,
                isTablet = isTablet,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                Box(
                    Modifier.weight(1f).graphicsLayer(translationY = avatarBob1),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    CrewAvatar(
                        color = OnboardingBrandBlue,
                        message = stringResource(R.string.onboarding_crew_question),
                        isTablet = isTablet,
                    )
                }
                Box(
                    Modifier.weight(1f).graphicsLayer(translationY = avatarBob2),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    CrewAvatar(
                        color = OnboardingTeal,
                        message = stringResource(R.string.onboarding_crew_reply),
                        isTablet = isTablet,
                    )
                }
            }
        }
        Spacer(
            Modifier.height(
                if (isTablet) TabletOnboardingTokens.CrewSectionSpacing else 20.dp,
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.spacedBy(
                    if (isTablet) TabletOnboardingTokens.CrewFeatureSpacing else 8.dp,
                ),
        ) {
            CrewFeature(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.onboarding_crew_crew),
                isTablet = isTablet,
            ) {
                Icon(
                    Icons.Default.Groups,
                    null,
                    tint = OnboardingBlue,
                    modifier =
                        Modifier.size(
                            if (isTablet) {
                                TabletOnboardingTokens.CrewFeatureIconSize
                            } else {
                                28.dp
                            },
                        ),
                )
            }
            CrewFeature(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.onboarding_crew_dates),
                isTablet = isTablet,
            ) {
                Icon(
                    Icons.Default.DateRange,
                    null,
                    tint = OnboardingOrange,
                    modifier =
                        Modifier.size(
                            if (isTablet) {
                                TabletOnboardingTokens.CrewFeatureIconSize
                            } else {
                                28.dp
                            },
                        ),
                )
            }
            CrewFeature(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.onboarding_crew_roles),
                isTablet = isTablet,
            ) {
                Icon(
                    Icons.Default.Person,
                    null,
                    tint = OnboardingTeal,
                    modifier =
                        Modifier.size(
                            if (isTablet) {
                                TabletOnboardingTokens.CrewFeatureIconSize
                            } else {
                                28.dp
                            },
                        ),
                )
            }
        }
    }
}

/** The light dashed arch visually connects the two crew members, as in the iOS card. */
@Composable
private fun CrewConnectionLine(
    modifier: Modifier = Modifier,
    dashPhase: Float = 0f,
    isTablet: Boolean = false,
) =
    Canvas(modifier) {
        val leftCenterX = size.width * .25f
        val rightCenterX = size.width * .75f
        val span = rightCenterX - leftCenterX
        val endpointY =
            size.height -
                if (isTablet) {
                    TabletOnboardingTokens.CrewConnectionEndpointInset.toPx()
                } else {
                    41.dp.toPx()
                }
        val controlY = size.height * .40f
        val connection = Path().apply {
            moveTo(leftCenterX, endpointY)
            cubicTo(
                leftCenterX + span * .28f,
                controlY,
                rightCenterX - span * .28f,
                controlY,
                rightCenterX,
                endpointY,
            )
        }
        drawPath(
            path = connection,
            color = Color(0xFFD4E2F0),
            style = Stroke(
                width =
                    if (isTablet) {
                        TabletOnboardingTokens.CrewConnectionStrokeWidth.toPx()
                    } else {
                        2.dp.toPx()
                    },
                cap = StrokeCap.Round,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                    if (isTablet) {
                        floatArrayOf(
                            TabletOnboardingTokens.CrewConnectionDashLength.toPx(),
                            TabletOnboardingTokens.CrewConnectionDashGap.toPx(),
                        )
                    } else {
                        floatArrayOf(7.dp.toPx(), 5.dp.toPx())
                    },
                    phase = dashPhase
                ),
            ),
        )
    }

@Composable
private fun CrewAvatar(
    color: Color,
    message: String,
    isTablet: Boolean,
) =
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            message,
            color = Color.White,
            fontSize =
                if (isTablet) {
                    TabletOnboardingTokens.CrewMessageFontSize
                } else {
                    15.sp
                },
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(
                    if (isTablet) {
                        TabletOnboardingTokens.CrewMessageShape
                    } else {
                        RoundedCornerShape(20.dp)
                    },
                )
                .background(color)
                .padding(
                    horizontal =
                        if (isTablet) {
                            TabletOnboardingTokens.CrewMessageHorizontalPadding
                        } else {
                            14.dp
                        },
                    vertical =
                        if (isTablet) {
                            TabletOnboardingTokens.CrewMessageVerticalPadding
                        } else {
                            10.dp
                        },
                ),
        )
        Spacer(
            Modifier.height(
                if (isTablet) TabletOnboardingTokens.CrewMessageAvatarSpacing else 10.dp,
            ),
        )
        Box(
            Modifier
                .size(if (isTablet) TabletOnboardingTokens.CrewAvatarSize else 82.dp)
                .clip(
                    if (isTablet) {
                        TabletOnboardingTokens.CrewAvatarShape
                    } else {
                        RoundedCornerShape(41.dp)
                    },
                )
                .background(color),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Person,
                null,
                tint = Color.White,
                modifier =
                    Modifier.size(
                        if (isTablet) TabletOnboardingTokens.CrewAvatarIconSize else 42.dp,
                    ),
            )
        }
    }

@Composable
private fun CrewFeature(
    modifier: Modifier,
    label: String,
    isTablet: Boolean,
    iconContent: @Composable () -> Unit,
) =
    Column(
        modifier = modifier
            .height(if (isTablet) TabletOnboardingTokens.CrewFeatureHeight else 86.dp)
            .clip(
                if (isTablet) {
                    TabletOnboardingTokens.CrewFeatureShape
                } else {
                    RoundedCornerShape(20.dp)
                },
            )
            .background(Color(0xFFF3F5F9)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        iconContent()
        Spacer(
            Modifier.height(
                if (isTablet) TabletOnboardingTokens.CrewFeatureLabelSpacing else 5.dp,
            ),
        )
        Text(
            label,
            color = OnboardingInk,
            fontSize =
                if (isTablet) {
                    TabletOnboardingTokens.CrewFeatureLabelFontSize
                } else {
                    12.sp
                },
            fontWeight = FontWeight.ExtraBold,
        )
    }

@Composable
private fun Disclaimer(
    accepted: Boolean,
    adaptiveLayout: AdaptiveLayout,
    onChanged: (Boolean) -> Unit,
) {
    val shape =
        if (adaptiveLayout.isTablet) {
            TabletOnboardingTokens.DisclaimerShape
        } else {
            RoundedCornerShape(22.dp)
        }
    val sizeModifier =
        if (adaptiveLayout.isTablet) {
            Modifier
                .widthIn(max = adaptiveLayout.overlayMaxWidth)
                .fillMaxWidth()
        } else {
            Modifier.fillMaxWidth()
        }
    Row(
        modifier = sizeModifier
            .clip(shape)
            .background(Color(0xFFF5F6FA))
            .border(
                if (adaptiveLayout.isTablet) {
                    TabletOnboardingTokens.DisclaimerBorderWidth
                } else {
                    1.5.dp
                },
                OnboardingTeal.copy(alpha = .55f),
                shape,
            )
            .padding(
                if (adaptiveLayout.isTablet) {
                    TabletOnboardingTokens.DisclaimerPadding
                } else {
                    12.dp
                },
            ),
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(
            checked = accepted,
            onCheckedChange = onChanged,
            modifier = Modifier.testTag("onboarding_disclaimer_checkbox"),
            colors = CheckboxDefaults.colors(checkedColor = OnboardingTeal, uncheckedColor = OnboardingTeal),
        )
        Text(
            stringResource(R.string.onboarding_disclaimer),
            color = OnboardingInk,
            fontSize =
                if (adaptiveLayout.isTablet) {
                    TabletOnboardingTokens.DisclaimerFontSize
                } else {
                    13.sp
                },
            lineHeight =
                if (adaptiveLayout.isTablet) {
                    TabletOnboardingTokens.DisclaimerLineHeight
                } else {
                    18.sp
                },
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .weight(1f)
                .padding(
                    top =
                        if (adaptiveLayout.isTablet) {
                            TabletOnboardingTokens.DisclaimerTextTopPadding
                        } else {
                            11.dp
                        },
                    end =
                        if (adaptiveLayout.isTablet) {
                            TabletOnboardingTokens.DisclaimerTextEndPadding
                        } else {
                            4.dp
                        },
                ),
        )
    }
}

@Composable
private fun PageIndicator(
    page: Int,
    adaptiveLayout: AdaptiveLayout,
) =
    Row(
        horizontalArrangement =
            Arrangement.spacedBy(
                if (adaptiveLayout.isTablet) {
                    TabletOnboardingTokens.DotSpacing
                } else {
                    9.dp
                },
            ),
    ) {
        repeat(ONBOARDING_PAGES) { index ->
            val width by animateFloatAsState(
                if (page == index) {
                    if (adaptiveLayout.isTablet) {
                        TabletOnboardingTokens.ActiveDotWidth.value
                    } else {
                        30f
                    }
                } else {
                    if (adaptiveLayout.isTablet) {
                        TabletOnboardingTokens.InactiveDotWidth.value
                    } else {
                        12f
                    }
                },
                label = "indicator",
            )
            val dotHeight =
                if (adaptiveLayout.isTablet) {
                    TabletOnboardingTokens.DotHeight
                } else {
                    12.dp
                }
            Box(
                Modifier
                    .width(width.dp)
                    .height(dotHeight)
                    .clip(RoundedCornerShape(dotHeight))
                    .background(
                        if (page == index) {
                            pageAccent(page)
                        } else {
                            Color(0xFFD5DDE2)
                        },
                    ),
            )
        }
    }

@Composable
private fun TideNodeMark(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "brand-waves")
    val wavePhase by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200), RepeatMode.Reverse),
        label = "brand-wave-phase"
    )
    Canvas(
        modifier
            .clip(RoundedCornerShape(13.dp))
            .background(TideNodeNavy)
    ) {
        val width = size.width
        val height = size.height

        // Two broad, asymmetric sails stay legible at launcher and 44 dp header sizes.
        drawPath(
            Path().apply {
                moveTo(width * .50f, height * .23f)
                cubicTo(
                    width * .60f,
                    height * .30f,
                    width * .69f,
                    height * .42f,
                    width * .73f,
                    height * .57f,
                )
                cubicTo(
                    width * .64f,
                    height * .54f,
                    width * .57f,
                    height * .54f,
                    width * .50f,
                    height * .56f,
                )
                close()
            },
            Color.White
        )
        drawPath(
            Path().apply {
                moveTo(width * .46f, height * .33f)
                cubicTo(
                    width * .38f,
                    height * .39f,
                    width * .31f,
                    height * .48f,
                    width * .27f,
                    height * .59f,
                )
                cubicTo(
                    width * .35f,
                    height * .56f,
                    width * .41f,
                    height * .55f,
                    width * .46f,
                    height * .56f,
                )
                close()
            },
            TideNodeSailWhite
        )

        drawPath(
            Path().apply {
                moveTo(width * .26f, height * .61f)
                cubicTo(
                    width * .36f,
                    height * .64f,
                    width * .62f,
                    height * .64f,
                    width * .74f,
                    height * .60f,
                )
                cubicTo(
                    width * .71f,
                    height * .66f,
                    width * .68f,
                    height * .69f,
                    width * .63f,
                    height * .71f,
                )
                cubicTo(
                    width * .51f,
                    height * .74f,
                    width * .39f,
                    height * .72f,
                    width * .31f,
                    height * .69f,
                )
                close()
            },
            Color.White
        )

        // Only the tide band moves, slowly and by well below one dp at 44 dp.
        val waveShift = wavePhase * width * .012f
        drawPath(
            Path().apply {
                moveTo(width * .343f + waveShift, height * .731f)
                cubicTo(
                    width * .407f + waveShift,
                    height * .694f,
                    width * .472f + waveShift,
                    height * .704f,
                    width * .528f + waveShift,
                    height * .731f,
                )
                cubicTo(
                    width * .583f + waveShift,
                    height * .759f,
                    width * .630f + waveShift,
                    height * .759f,
                    width * .657f + waveShift,
                    height * .731f,
                )
                lineTo(width * .657f + waveShift, height * .759f)
                cubicTo(
                    width * .620f + waveShift,
                    height * .778f,
                    width * .574f + waveShift,
                    height * .778f,
                    width * .519f + waveShift,
                    height * .769f,
                )
                cubicTo(
                    width * .463f + waveShift,
                    height * .741f,
                    width * .407f + waveShift,
                    height * .750f,
                    width * .361f + waveShift,
                    height * .769f,
                )
                close()
            },
            TideNodeCyan
        )
    }
}

private fun pageAccent(page: Int): Color = when(page) { 0 -> OnboardingBlue; 1 -> OnboardingOrange; else -> OnboardingTeal }
