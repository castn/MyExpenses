package org.totschnig.myexpenses.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min

/**
 * A scene of the design system's illustrations at the top of a page that introduces something,
 * e.g. a page of the first start, in the colors of the theme. Grows into place when shown.
 *
 * @param id one of the drawables R.drawable.illustration_*
 * @param maxHeight smaller where there is less room, e.g. above the content in [IntroPage]
 */
@Composable
fun HeroIllustration(
    @DrawableRes id: Int,
    modifier: Modifier = Modifier,
    maxHeight: Dp = 280.dp,
) {
    val image = illustration(id)
    Image(
        image,
        contentDescription = null,
        modifier = modifier
            .heightIn(max = maxHeight)
            .aspectRatio(image.viewportWidth / image.viewportHeight, matchHeightConstraintsFirst = true)
            .growIn()
    )
}

@Composable
private fun Modifier.growIn(): Modifier {
    val scale = remember { Animatable(0.85f) }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

/**
 * Where the user is in a sequence of pages: a dot per page, the current one stretched
 *
 * @param description read out instead of the dots, e.g. "Step 2 of 4"
 */
@Composable
fun StepIndicator(count: Int, current: Int, description: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { index ->
            val width by animateDpAsState(if (index == current) 24.dp else 8.dp, label = "step width")
            Box(
                Modifier
                    .size(width, 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (index <= current) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHighest
                    )
            )
        }
    }
}

/**
 * One thing that something offers, with an icon in front, e.g. a feature on a page of the first start
 */
@Composable
fun FeatureRow(icon: ImageVector, title: String, text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(color = MaterialTheme.colorScheme.secondaryContainer) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Column(Modifier.padding(start = Spacing.content)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * A page that introduces something, e.g. one step of the first start: [hero] above title and text,
 * then [content]. On wide screens the hero stands to the left of the rest. Above the rest it takes
 * at most a third of the height and is left out where there is little room, e.g. while the keyboard
 * is open.
 */
@Composable
fun IntroPage(
    title: String,
    text: String?,
    modifier: Modifier = Modifier,
    hero: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val heading = @Composable {
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() }
            )
            if (text != null) {
                Text(
                    text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
        val heroAbove = if (maxHeight >= 480.dp) min(maxHeight / 3, 240.dp) else null
        if (maxWidth >= 600.dp) {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = Spacing.section),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { hero() }
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = Spacing.section),
                    verticalArrangement = Arrangement.Center
                ) {
                    heading()
                    Spacer(Modifier.height(Spacing.section))
                    content()
                }
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.section, vertical = Spacing.section)
            ) {
                if (heroAbove != null) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = heroAbove)
                            .padding(top = 24.dp, bottom = 32.dp),
                        contentAlignment = Alignment.Center
                    ) { hero() }
                }
                heading()
                Spacer(Modifier.height(Spacing.section))
                content()
            }
        }
    }
}

/**
 * The bar below the pages of a sequence: skipping on the left, going on on the right
 *
 * @param skipLabel null if the current page cannot be skipped
 */
@Composable
fun IntroBottomBar(
    nextLabel: String,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    nextEnabled: Boolean = true,
    skipLabel: String? = null,
    onSkip: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = Spacing.screen, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (skipLabel != null) {
            TextButton(onClick = onSkip) { Text(skipLabel) }
        }
        Spacer(Modifier.weight(1f))
        Button(onClick = onNext, enabled = nextEnabled, modifier = Modifier.widthIn(min = 120.dp)) {
            Text(nextLabel)
        }
    }
}

/**
 * One of a few ways to go on, picked like a radio button, e.g. how to start the app. Shows
 * [details] below its text while picked.
 */
@Composable
fun ChoiceCard(
    icon: ImageVector,
    title: String,
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    details: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Surface(
        shape = Shapes.card,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .clip(Shapes.card)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
    ) {
        Column(Modifier.padding(Spacing.content)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer) {
                    Icon(
                        icon, contentDescription = null,
                        tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = Spacing.content)
                ) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // The whole card is selectable, the radio button only shows the state
                RadioButton(selected = selected, onClick = null)
            }
            if (details != null) {
                AnimatedVisibility(visible = selected) {
                    Column(Modifier.padding(top = 12.dp), content = details)
                }
            }
        }
    }
}

/**
 * One of the ways a [ChoiceCard] offers in its details, e.g. where to restore from. Several of
 * them go into a column with [Modifier.selectableGroup].
 */
@Composable
fun ChoiceOption(
    icon: ImageVector,
    title: String,
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = Shapes.tile,
        color = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier
            .fillMaxWidth()
            .clip(Shapes.tile)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Icon(
                icon, contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RadioButton(selected = selected, onClick = null)
        }
    }
}
