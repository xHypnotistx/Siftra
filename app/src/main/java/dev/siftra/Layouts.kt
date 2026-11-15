@file:OptIn(ExperimentalSharedTransitionApi::class, ExperimentalLayoutApi::class)

package dev.siftra

import android.graphics.Rect
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sqrt

enum class HomeLayout(val title: String) { CARDS("Cards"), STRATA("Strata"), MONOLITH("Monolith"), ORBIT("Orbit"), INDEX("Index") }

/** Tap opens the group, long-press opens rename/delete. No ripple (glass shows its own press). */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun Modifier.groupClick(onClick: () -> Unit, onLongClick: () -> Unit) = composed {
    combinedClickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null, onLongClick = onLongClick, onClick = onClick)
}

/** Wraps a group's home element so it container-transforms into the open group panel. */
@Composable
fun SharedSource(sts: SharedTransitionScope, visible: Boolean, g: Group, modifier: Modifier, content: @Composable (Modifier) -> Unit) {
    Box(modifier) {
        with(sts) {
            AnimatedVisibility(visible, enter = fadeIn(tween(220)), exit = fadeOut(tween(120))) {
                content(Modifier.sharedBounds(rememberSharedContentState("g_${g.name}"), this@AnimatedVisibility,
                    boundsTransform = { _, _ -> spring(dampingRatio = 0.84f, stiffness = 360f) },
                    resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds()))
            }
        }
    }
}

@Composable
fun GroupsArea(
    s: Siftra, sts: SharedTransitionScope, openGroup: Group?, sections: List<Pair<Group, List<AppItem>>>,
    onGroup: (Group) -> Unit, onLaunch: (AppItem, Rect?) -> Unit, onLong: (AppItem) -> Unit,
    conn: NestedScrollConnection, modifier: Modifier, overlay: Boolean = false, onGroupLong: (Group) -> Unit = {},
) {
    val ui = LocalUi.current
    val ink = LocalInk.current
    when (s.layout) {
        /* ---------- Strata: offset glass slabs, like rock layers ---------- */
        HomeLayout.STRATA -> LazyColumn(modifier.nestedScroll(conn), verticalArrangement = Arrangement.spacedBy(9.dp),
            contentPadding = PaddingValues(bottom = 12.dp)) {
            itemsIndexed(sections, key = { _, it -> it.first.name }) { i, (g, list) ->
                val off = listOf(0, 10, 20, 10, 0, 14)[i % 6].dp
                SharedSource(sts, openGroup != g, g, Modifier.animateItem().padding(start = off, end = 20.dp - off).fillMaxWidth().height(66.dp)) { sm ->
                    Row(sm.fillMaxSize().glass(22.dp).groupClick({ onGroup(g) }, { onGroupLong(g) }).padding(start = 18.dp, end = 14.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(g.title, 23.sp, display = true, maxLines = 1)
                        Spacer(Modifier.width(10.dp))
                        GroupDot(list)
                        Text("${list.size}", 12.sp, FontWeight.SemiBold, color = ui.accent)
                        Spacer(Modifier.weight(1f))
                        val shown = list.take(4)
                        Box(Modifier.width((34 + 24 * (shown.size - 1).coerceAtLeast(0)).dp).height(34.dp)) {
                            shown.forEachIndexed { j, a -> Box(Modifier.offset(x = (24 * j).dp)) { AppGlyph(a, 34.dp) } }
                        }
                    }
                }
            }
        }

        /* ---------- Monolith: ribbed bento cards with big serif counts ---------- */
        HomeLayout.MONOLITH -> LazyVerticalGrid(GridCells.Fixed(2), modifier.nestedScroll(conn),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 12.dp)) {
            items(sections, key = { it.first.name }) { (g, list) ->
                SharedSource(sts, openGroup != g, g, Modifier.animateItem().height(150.dp)) { sm ->
                    Column(sm.fillMaxSize().glass(24.dp).groupClick({ onGroup(g) }, { onGroupLong(g) }).padding(16.dp)) {
                        T(g.title.uppercase(), 10.5.sp, weight = FontWeight.SemiBold, alpha = 0.8f, tracking = 2.2.sp, maxLines = 1)
                        Spacer(Modifier.weight(1f))
                        Row(verticalAlignment = Alignment.Bottom) {
                            T("${list.size}", 54.sp, display = true, lineHeight = 50.sp)
                            GroupDot(list, Modifier.align(Alignment.Top).padding(top = 10.dp))
                            Spacer(Modifier.weight(1f))
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                list.take(4).chunked(2).forEach { r -> Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { r.forEach { AppGlyph(it, 30.dp) } } }
                            }
                        }
                    }
                }
            }
        }

        /* ---------- Orbit: glass bubbles sized by app count ---------- */
        HomeLayout.ORBIT -> {
            val max = sections.maxOfOrNull { it.second.size }?.coerceAtLeast(1) ?: 1
            FlowRow(
                modifier.nestedScroll(conn).verticalScroll(rememberScrollState()).padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                sections.forEach { (g, list) ->
                    val d: Dp = (84 + 74 * sqrt(list.size.toFloat() / max)).dp
                    val big = d > 112.dp
                    SharedSource(sts, openGroup != g, g, Modifier.size(d)) { sm ->
                        Column(sm.fillMaxSize().glass(d / 2).groupClick({ onGroup(g) }, { onGroupLong(g) }).padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text(g.title, if (big) 19.sp else 15.sp, display = true, center = true, maxLines = 1)
                            Row(verticalAlignment = Alignment.CenterVertically) { GroupDot(list); Text("${list.size} apps", 11.sp, FontWeight.SemiBold, color = ui.accent) }
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                list.take(if (big) 4 else 3).forEach { AppGlyph(it, if (big) 26.dp else 22.dp) }
                            }
                        }
                    }
                }
            }
        }

        /* ---------- Index Lime: living editorial list, tap / double-tap-drag to unfold ---------- */
        HomeLayout.INDEX -> IndexLime(s, sections, overlay, onLaunch, onLong, conn, modifier, onGroupLong)

        HomeLayout.CARDS -> {}
    }
}
