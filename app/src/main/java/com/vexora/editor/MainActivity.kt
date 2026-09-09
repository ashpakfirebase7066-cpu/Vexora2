package com.vexora.editor

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pointerInput
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.consume
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlin.math.max
import kotlin.math.roundToLong

private val Bg = Color(0xFF111216)
private val Panel2 = Color(0xFF24252C)
private val Accent = Color(0xFF8B5CF6)
private val TimelineAccent = Color(0xFFE6C84A)
private val PrimaryText = Color(0xFFF4F4F6)
private val SecondaryText = Color(0xFF9799A5)

private data class Clip(val id: Int, val uri: Uri, val video: Boolean, val duration: Long)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { VexoraEditor() }
    }
}

@Composable
private fun VexoraEditor() {
    val context = LocalContext.current
    val clips = remember { mutableStateListOf<Clip>() }
    var selected by remember { mutableIntStateOf(-1) }
    var toolbarVisible by remember { mutableStateOf(false) }
    var nextId by remember { mutableIntStateOf(1) }

    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(20)
    ) { uris ->
        uris.forEach { uri ->
            val type = context.contentResolver.getType(uri).orEmpty()
            val video = type.startsWith("video")
            clips += Clip(nextId++, uri, video, if (video) videoDuration(context, uri) else 3000L)
        }
        if (selected < 0 && clips.isNotEmpty()) selected = 0
    }

    fun openPicker() = picker.launch(
        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
    )

    fun updateSelectedDuration(newDuration: Long) {
        if (selected in clips.indices) {
            val old = clips[selected]
            clips[selected] = old.copy(duration = newDuration.coerceIn(500L, 60000L))
        }
    }

    fun duplicateSelected() {
        if (selected in clips.indices) {
            val copy = clips[selected].copy(id = nextId++)
            clips.add(selected + 1, copy)
            selected++
        }
    }

    Column(Modifier.fillMaxSize().background(Bg)) {
        EditorTopBar(onMedia = ::openPicker)
        Preview(clips.getOrNull(selected))
        TimeRow(clips.sumOf { it.duration })
        Timeline(
            clips = clips,
            selected = selected,
            onSelect = { index ->
                if (selected == index) {
                    toolbarVisible = !toolbarVisible
                } else {
                    selected = index
                    toolbarVisible = true
                }
            },
            onAdd = ::openPicker,
            onDelete = {
                if (selected in clips.indices) {
                    clips.removeAt(selected)
                    selected = if (clips.isEmpty()) -1 else selected.coerceAtMost(clips.lastIndex)
                    toolbarVisible = false
                }
            },
            onDuplicate = ::duplicateSelected,
            onAction = { name ->
                if (name == "Replace") openPicker()
                else Toast.makeText(context, "$name selected", Toast.LENGTH_SHORT).show()
            },
            onResize = { deltaPx ->
                if (selected in clips.indices && !clips[selected].video) {
                    val deltaMs = (deltaPx / 55f * 1000f).roundToLong()
                    updateSelectedDuration(clips[selected].duration + deltaMs)
                }
            },
            onLeft = {
                if (selected > 0) {
                    val x = clips.removeAt(selected)
                    clips.add(selected - 1, x)
                    selected--
                }
            },
            onRight = {
                if (selected in 0 until clips.lastIndex) {
                    val x = clips.removeAt(selected)
                    clips.add(selected + 1, x)
                    selected++
                }
            },
            toolbarVisible = toolbarVisible
        )
        BottomTools(onMedia = ::openPicker) { name ->
            Toast.makeText(context, "$name tool coming next", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
private fun EditorTopBar(onMedia: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).background(Color(0xFF15161A)).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = {}) { Icon(Icons.Default.ArrowBack, "Back", tint = PrimaryText) }
        Icon(Icons.Default.FolderOpen, "Project", tint = SecondaryText)
        Text(
            "Vexora 2",
            color = PrimaryText,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 10.dp).weight(1f)
        )
        Text("Original", color = PrimaryText, fontSize = 12.sp)
        IconButton(onClick = {}) { Icon(Icons.Default.MoreHoriz, "More", tint = SecondaryText) }
        Button(
            onClick = onMedia,
            colors = ButtonDefaults.buttonColors(containerColor = Accent),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp)
        ) {
            Icon(Icons.Default.Add, "Media", Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Media", fontSize = 12.sp)
        }
    }
}

@Composable
private fun Preview(clip: Clip?) {
    Box(
        Modifier.fillMaxWidth().height(260.dp).background(Color(0xFF0C0D10)),
        contentAlignment = Alignment.Center
    ) {
        when {
            clip == null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.VideoLibrary, null, tint = SecondaryText, modifier = Modifier.size(46.dp))
                Spacer(Modifier.height(8.dp))
                Text("Add image or video to start", color = SecondaryText, fontSize = 13.sp)
            }
            clip.video -> VideoPlayer(clip.uri)
            else -> AsyncImage(
                clip.uri,
                "Image preview",
                Modifier.fillMaxSize().padding(8.dp),
                contentScale = ContentScale.Fit
            )
        }
        if (clip != null) {
            IconButton(
                onClick = {},
                modifier = Modifier.align(Alignment.BottomCenter)
                    .background(Color.Black.copy(.55f), RoundedCornerShape(50))
            ) { Icon(Icons.Default.PlayArrow, "Play", tint = Color.White) }
        }
        IconButton(onClick = {}, modifier = Modifier.align(Alignment.BottomEnd)) {
            Icon(Icons.Default.Fullscreen, "Fullscreen", tint = Color.White)
        }
    }
}

@Composable
private fun VideoPlayer(uri: Uri) {
    val context = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        factory = { PlayerView(it).apply { this.player = player; useController = true } },
        update = { it.player = player },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun TimeRow(total: Long) {
    Row(
        Modifier.fillMaxWidth().height(42.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("00:00 / ${time(total)}", color = PrimaryText, fontSize = 12.sp)
        Spacer(Modifier.weight(1f))
        Icon(Icons.Default.Tune, "Adjust", tint = SecondaryText, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Icon(Icons.Default.Undo, "Undo", tint = SecondaryText, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Icon(Icons.Default.Redo, "Redo", tint = SecondaryText, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun Timeline(
    clips: List<Clip>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onAdd: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onAction: (String) -> Unit,
    onResize: (Float) -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    toolbarVisible: Boolean
) {
    val timelineScroll = rememberScrollState()
    val totalDuration = clips.sumOf { it.duration }.coerceAtLeast(3000L)
    val pixelsPerSecond = 55f
    val contentWidth = (totalDuration / 1000f * pixelsPerSecond).coerceAtLeast(360f)

    Column(Modifier.fillMaxWidth().height(300.dp).background(Color(0xFF15161A))) {
        Row(
            Modifier.fillMaxWidth().height(36.dp).background(Color(0xFF121318)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "TRACKS",
                color = SecondaryText,
                fontSize = 8.sp,
                modifier = Modifier.width(76.dp).padding(start = 12.dp)
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onLeft, enabled = selected > 0, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Default.ArrowBack,
                    "Move left",
                    tint = if (selected > 0) PrimaryText else Color(0xFF4B4C55),
                    modifier = Modifier.size(17.dp)
                )
            }
            IconButton(
                onClick = onRight,
                enabled = selected >= 0 && selected < clips.lastIndex,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    Icons.Default.ArrowForward,
                    "Move right",
                    tint = if (selected >= 0 && selected < clips.lastIndex) PrimaryText else Color(0xFF4B4C55),
                    modifier = Modifier.size(17.dp)
                )
            }
            IconButton(onClick = onDelete, enabled = selected >= 0, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Default.Delete,
                    "Delete",
                    tint = if (selected >= 0) Color(0xFFE56B73) else Color(0xFF4B4C55),
                    modifier = Modifier.size(17.dp)
                )
            }
            Spacer(Modifier.width(5.dp))
        }

        Row(Modifier.fillMaxWidth().height(264.dp)) {
            Column(
                Modifier.width(76.dp).fillMaxHeight().background(Color(0xFF17181D)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TrackButton(Icons.Default.LibraryMusic, "Music")
                TrackButton(Icons.Default.TextFields, "Subtitle")
                TrackButton(Icons.Default.Image, "Overlay")
                TrackButton(Icons.Default.VideoLibrary, "Video")
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Cover", color = SecondaryText, fontSize = 8.sp)
                }
            }

            BoxWithConstraints(Modifier.fillMaxHeight().weight(1f)) {
                val visibleWidth = max(maxWidth.value, contentWidth)
                Column(Modifier.fillMaxHeight().horizontalScroll(timelineScroll)) {
                    TimelineLane(44.dp, visibleWidth, "Tap to add music")
                    TimelineLane(44.dp, visibleWidth, "Tap to add subtitle")
                    TimelineLane(44.dp, visibleWidth, "Tap to add sticker / overlay")
                    MainMediaLane(
                        clips = clips,
                        selected = selected,
                        onSelect = onSelect,
                        onAdd = onAdd,
                        onDuplicate = onDuplicate,
                        onAction = onAction,
                        onResize = onResize,
                        contentWidth = visibleWidth,
                        toolbarVisible = toolbarVisible
                    )
                    TimeMarkers(clips, visibleWidth, pixelsPerSecond)
                }
            }
        }
    }
}

@Composable
private fun TrackButton(icon: ImageVector, label: String) {
    Box(
        Modifier.fillMaxWidth().height(44.dp).border(0.5.dp, Color(0xFF24252C)),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, label, tint = SecondaryText, modifier = Modifier.size(18.dp))
            Box(
                Modifier.padding(start = 2.dp).size(13.dp)
                    .background(Color(0xFF2C2D34), RoundedCornerShape(50)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Add, "Add $label", tint = PrimaryText, modifier = Modifier.size(11.dp))
            }
        }
    }
}

@Composable
private fun TimelineLane(height: Dp, contentWidth: Float, hint: String) {
    Box(
        Modifier.width(contentWidth.dp).height(height).border(0.5.dp, Color(0xFF2A2B32)),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier.padding(start = 8.dp).width(208.dp).height(30.dp)
                .background(Color(0xFF24262D), RoundedCornerShape(3.dp)),
            contentAlignment = Alignment.CenterStart
        ) {
            Icon(Icons.Default.Add, null, tint = SecondaryText, modifier = Modifier.padding(start = 7.dp).size(15.dp))
            Text(hint, color = Color(0xFF777984), fontSize = 10.sp, modifier = Modifier.padding(start = 28.dp))
        }
    }
}

@Composable
private fun MainMediaLane(
    clips: List<Clip>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onAdd: () -> Unit,
    onDuplicate: () -> Unit,
    onAction: (String) -> Unit,
    onResize: (Float) -> Unit,
    contentWidth: Float,
    toolbarVisible: Boolean
) {
    Box(
        Modifier.width(contentWidth.dp).height(96.dp)
            .background(Color(0xFF1B1C22))
            .border(0.5.dp, Color(0xFF34353D))
    ) {
        if (clips.isEmpty()) {
            Box(
                Modifier.padding(start = 8.dp, top = 10.dp).width(280.dp).height(74.dp)
                    .border(1.dp, Color(0xFF3B3C45), RoundedCornerShape(5.dp))
                    .clickable(onClick = onAdd),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, null, tint = SecondaryText)
                    Spacer(Modifier.width(8.dp))
                    Text("Tap to add photo or video", color = SecondaryText, fontSize = 11.sp)
                }
            }
        } else {
            Row(Modifier.fillMaxHeight().padding(start = 8.dp, top = 10.dp, bottom = 10.dp)) {
                clips.forEachIndexed { index, clip ->
                    val width = (clip.duration / 1000f * 55f).coerceIn(58f, 360f)
                    TimelineClip(
                        clip = clip,
                        selected = index == selected,
                        width = width.dp,
                        onClick = { onSelect(index) },
                        onResize = if (index == selected && !clip.video) onResize else null
                    )
                }
                Box(
                    Modifier.padding(start = 6.dp).width(58.dp).fillMaxHeight()
                        .border(1.dp, Color(0xFF3B3C45), RoundedCornerShape(5.dp))
                        .clickable(onClick = onAdd),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, "Add media", tint = SecondaryText, modifier = Modifier.size(28.dp))
                }
            }

            if (toolbarVisible && selected in clips.indices) {
                val toolbarStartPx = 8f + clips.take(selected).sumOf {
                    (it.duration / 1000f * 55f).coerceIn(58f, 360f).toDouble()
                }.toFloat()
                SelectedClipToolbar(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = toolbarStartPx.dp, y = (-62).dp),
                    onDuplicate = onDuplicate,
                    onAction = onAction
                )
            }
        }
    }
}

@Composable
private fun SelectedClipToolbar(
    modifier: Modifier,
    onDuplicate: () -> Unit,
    onAction: (String) -> Unit
) {
    Row(
        modifier
            .height(58.dp)
            .background(Color(0xFFFFD400), RoundedCornerShape(12.dp))
            .padding(horizontal = 7.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ClipAction(Icons.Default.Refresh, "Replace") { onAction("Replace") }
        ClipAction(Icons.Default.Star, "Keyframe") { onAction("Keyframe") }
        ClipAction(Icons.Default.ShowChart, "Curve") { onAction("Curve") }
        ClipAction(Icons.Default.Lock, "Lock") { onAction("Lock") }
        ClipAction(Icons.Default.ContentCopy, "Duplicate") { onDuplicate() }
        ClipAction(Icons.Default.Delete, "Delete") { onAction("Delete") }
    }
}

@Composable
private fun ClipAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.width(54.dp).fillMaxHeight().clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, label, tint = Color(0xFF17181D), modifier = Modifier.size(23.dp))
        Text(label, color = Color(0xFF17181D), fontSize = 8.sp, maxLines = 1)
    }
}

@Composable
private fun TimelineClip(
    clip: Clip,
    selected: Boolean,
    width: Dp,
    onClick: () -> Unit,
    onResize: ((Float) -> Unit)?
) {
    Box(
        Modifier.padding(end = 3.dp).width(width).fillMaxHeight().clip(RoundedCornerShape(4.dp))
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) TimelineAccent else Color(0xFF3B3C45),
                RoundedCornerShape(4.dp)
            )
            .background(Panel2)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (clip.video) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.VideoLibrary, null, tint = Color.White, modifier = Modifier.size(22.dp))
                Text("VIDEO", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            AsyncImage(clip.uri, "Timeline image", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Text(
            time(clip.duration),
            color = Color.White,
            fontSize = 8.sp,
            modifier = Modifier.align(Alignment.BottomEnd)
                .background(Color.Black.copy(.65f))
                .padding(3.dp)
        )

        if (onResize != null) {
            Box(
                Modifier.align(Alignment.CenterEnd)
                    .width(24.dp).fillMaxHeight()
                    .pointerInput(clip.id) {
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onResize(dragAmount.x)
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier.width(6.dp).fillMaxHeight().padding(vertical = 8.dp)
                        .background(TimelineAccent, RoundedCornerShape(4.dp))
                )
                Icon(
                    Icons.Default.ChevronRight,
                    "Drag to change image duration",
                    tint = Color.Black,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun TimeMarkers(clips: List<Clip>, contentWidth: Float, pixelsPerSecond: Float) {
    Row(
        Modifier.width(contentWidth.dp).height(28.dp).background(Color(0xFF15161A)),
        verticalAlignment = Alignment.Top
    ) {
        if (clips.isNotEmpty()) {
            var elapsed = 0L
            clips.forEach { clip ->
                val width = (clip.duration / 1000f * pixelsPerSecond).coerceIn(58f, 360f)
                Box(Modifier.width(width.dp).padding(top = 3.dp)) {
                    Text(time(elapsed), color = SecondaryText, fontSize = 8.sp)
                    elapsed += clip.duration
                }
            }
        } else {
            Text("00:00", color = SecondaryText, fontSize = 8.sp, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

@Composable
private fun BottomTools(onMedia: () -> Unit, onTool: (String) -> Unit) {
    val tools = listOf(
        "Media" to Icons.Default.VideoLibrary,
        "Trim" to Icons.Default.Crop,
        "Split" to Icons.Default.ContentCut,
        "Speed" to Icons.Default.Speed,
        "Volume" to Icons.Default.VolumeUp,
        "Text" to Icons.Default.TextFields,
        "Audio" to Icons.Default.AudioFile,
        "Filter" to Icons.Default.FilterAlt,
        "Flip" to Icons.Default.Flip,
        "Mosaic" to Icons.Default.GridOn,
        "Record" to Icons.Default.Mic
    )
    Row(
        Modifier.fillMaxWidth().height(82.dp).background(Color(0xFF121318))
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tools.forEach { (name, icon) ->
            Column(
                Modifier.width(70.dp).clickable { if (name == "Media") onMedia() else onTool(name) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(icon, name, tint = if (name == "Media") Color.White else SecondaryText, modifier = Modifier.size(25.dp))
                Spacer(Modifier.height(4.dp))
                Text(name, color = if (name == "Media") Color.White else SecondaryText, fontSize = 10.sp)
            }
        }
    }
}

private fun videoDuration(context: Context, uri: Uri): Long {
    val r = MediaMetadataRetriever()
    return try {
        r.setDataSource(context, uri)
        r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 3000L
    } catch (_: Exception) {
        3000L
    } finally {
        r.release()
    }
}

private fun time(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d".format(s / 60, s % 60)
}