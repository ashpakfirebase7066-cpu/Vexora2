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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage

private val Bg = Color(0xFF111216)
private val Panel = Color(0xFF191A20)
private val Panel2 = Color(0xFF24252C)
private val Accent = Color(0xFF8B5CF6)
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
    fun openPicker() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))

    Column(Modifier.fillMaxSize().background(Bg)) {
        EditorTopBar(onMedia = ::openPicker)
        Preview(clip = clips.getOrNull(selected))
        TimeRow(clips.sumOf { it.duration })
        Timeline(
            clips = clips,
            selected = selected,
            onSelect = { selected = it },
            onAdd = ::openPicker,
            onDelete = {
                if (selected in clips.indices) {
                    clips.removeAt(selected)
                    selected = if (clips.isEmpty()) -1 else selected.coerceAtMost(clips.lastIndex)
                }
            },
            onLeft = {
                if (selected > 0) {
                    val x = clips.removeAt(selected); clips.add(selected - 1, x); selected--
                }
            },
            onRight = {
                if (selected in 0 until clips.lastIndex) {
                    val x = clips.removeAt(selected); clips.add(selected + 1, x); selected++
                }
            }
        )
        BottomTools(onMedia = ::openPicker) { name ->
            Toast.makeText(context, "$name tool coming next", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
private fun EditorTopBar(onMedia: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(56.dp).background(Color(0xFF15161A)).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = {}) { Icon(Icons.Default.ArrowBack, "Back", tint = PrimaryText) }
        Icon(Icons.Default.FolderOpen, "Project", tint = SecondaryText)
        Text("Vexora 2", color = PrimaryText, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp).weight(1f))
        Text("Original", color = PrimaryText, fontSize = 12.sp)
        IconButton(onClick = {}) { Icon(Icons.Default.MoreHoriz, "More", tint = SecondaryText) }
        Button(onClick = onMedia, colors = ButtonDefaults.buttonColors(containerColor = Accent), shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
            Icon(Icons.Default.Add, "Media", Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Media", fontSize = 12.sp)
        }
    }
}

@Composable
private fun Preview(clip: Clip?) {
    Box(Modifier.fillMaxWidth().height(260.dp).background(Color(0xFF0C0D10)), contentAlignment = Alignment.Center) {
        when {
            clip == null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.VideoLibrary, null, tint = SecondaryText, modifier = Modifier.size(46.dp))
                Spacer(Modifier.height(8.dp)); Text("Add image or video to start", color = SecondaryText, fontSize = 13.sp)
            }
            clip.video -> VideoPlayer(clip.uri)
            else -> AsyncImage(clip.uri, "Image preview", Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit)
        }
        if (clip != null) {
            IconButton(onClick = {}, modifier = Modifier.align(Alignment.BottomCenter).background(Color.Black.copy(.55f), RoundedCornerShape(50))) {
                Icon(Icons.Default.PlayArrow, "Play", tint = Color.White)
            }
        }
        IconButton(onClick = {}, modifier = Modifier.align(Alignment.BottomEnd)) { Icon(Icons.Default.Fullscreen, "Fullscreen", tint = Color.White) }
    }
}

@Composable
private fun VideoPlayer(uri: Uri) {
    val context = LocalContext.current
    val player = remember(uri) { ExoPlayer.Builder(context).build().apply { setMediaItem(MediaItem.fromUri(uri)); prepare() } }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        factory = { PlayerView(it).apply { this.player = player; useController = true } },
        update = { it.player = player },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun TimeRow(total: Long) {
    Row(Modifier.fillMaxWidth().height(42.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("00:00 / ${time(total)}", color = PrimaryText, fontSize = 12.sp)
        Spacer(Modifier.weight(1f))
        Icon(Icons.Default.Tune, "Adjust", tint = SecondaryText, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp)); Icon(Icons.Default.Undo, "Undo", tint = SecondaryText, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(14.dp)); Icon(Icons.Default.Redo, "Redo", tint = SecondaryText, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun Timeline(clips: List<Clip>, selected: Int, onSelect: (Int) -> Unit, onAdd: () -> Unit, onDelete: () -> Unit, onLeft: () -> Unit, onRight: () -> Unit) {
    Column(Modifier.fillMaxWidth().height(300.dp).background(Color(0xFF15161A))) {
        Row(Modifier.fillMaxWidth().height(42.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TimelineAction(Icons.Default.LibraryMusic, "Music"); TimelineAction(Icons.Default.TextFields, "Text"); TimelineAction(Icons.Default.Image, "Overlay"); TimelineAction(Icons.Default.VideoLibrary, "Clip")
            Spacer(Modifier.weight(1f)); IconButton(onClick = onLeft, enabled = selected > 0) { Icon(Icons.Default.ArrowBack, "Move left", tint = SecondaryText) }; IconButton(onClick = onRight, enabled = selected >= 0 && selected < clips.lastIndex) { Icon(Icons.Default.ArrowForward, "Move right", tint = SecondaryText) }; IconButton(onClick = onDelete, enabled = selected >= 0) { Icon(Icons.Default.Delete, "Delete", tint = Color(0xFFE56B73)) }
        }
        Row(Modifier.fillMaxWidth().height(112.dp).padding(start = 76.dp, end = 10.dp).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            if (clips.isEmpty()) {
                Box(Modifier.width(320.dp).height(76.dp).border(1.dp, Color(0xFF383942), RoundedCornerShape(6.dp)).clickable(onClick = onAdd), contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Add, null, tint = SecondaryText); Spacer(Modifier.width(8.dp)); Text("Tap to add photo or video", color = SecondaryText, fontSize = 12.sp) }
                }
            } else {
                clips.forEachIndexed { i, clip -> ClipCard(clip, i == selected) { onSelect(i) } }
                Box(Modifier.padding(start = 6.dp).size(82.dp, 76.dp).border(1.dp, Color(0xFF383942), RoundedCornerShape(6.dp)).clickable(onClick = onAdd), contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, "Add", tint = SecondaryText, modifier = Modifier.size(30.dp)) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 76.dp, end = 10.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) { clips.forEach { Text(time(it.duration), color = SecondaryText, fontSize = 9.sp) } }
    }
}

@Composable
private fun TimelineAction(icon: ImageVector, label: String) {
    Column(Modifier.width(62.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, label, tint = SecondaryText, modifier = Modifier.size(19.dp)); Text(label, color = SecondaryText, fontSize = 8.sp) }
}

@Composable
private fun ClipCard(clip: Clip, selected: Boolean, onClick: () -> Unit) {
    Box(Modifier.padding(end = 3.dp).size(112.dp, 76.dp).clip(RoundedCornerShape(5.dp)).border(if (selected) 2.dp else 1.dp, if (selected) Accent else Color(0xFF3B3C45), RoundedCornerShape(5.dp)).background(Panel2).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        if (clip.video) {
            Icon(Icons.Default.VideoLibrary, null, tint = Color.White, modifier = Modifier.size(28.dp)); Text("VIDEO", color = Color.White, fontSize = 8.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 7.dp))
        } else AsyncImage(clip.uri, "Timeline image", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Text(time(clip.duration), color = Color.White, fontSize = 8.sp, modifier = Modifier.align(Alignment.BottomEnd).background(Color.Black.copy(.65f)).padding(3.dp))
    }
}

@Composable
private fun BottomTools(onMedia: () -> Unit, onTool: (String) -> Unit) {
    val tools = listOf("Media" to Icons.Default.VideoLibrary, "Trim" to Icons.Default.Crop, "Split" to Icons.Default.ContentCut, "Speed" to Icons.Default.Speed, "Volume" to Icons.Default.VolumeUp, "Text" to Icons.Default.TextFields, "Audio" to Icons.Default.AudioFile, "Filter" to Icons.Default.FilterAlt, "Flip" to Icons.Default.Flip, "Mosaic" to Icons.Default.GridOn, "Record" to Icons.Default.Mic)
    Row(Modifier.fillMaxWidth().height(82.dp).background(Color(0xFF121318)).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
        tools.forEach { (name, icon) -> Column(Modifier.width(70.dp).clickable { if (name == "Media") onMedia() else onTool(name) }, horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, name, tint = if (name == "Media") Color.White else SecondaryText, modifier = Modifier.size(25.dp)); Spacer(Modifier.height(4.dp)); Text(name, color = if (name == "Media") Color.White else SecondaryText, fontSize = 10.sp) } }
    }
}

private fun videoDuration(context: Context, uri: Uri): Long {
    val r = MediaMetadataRetriever()
    return try { r.setDataSource(context, uri); r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 3000L } catch (_: Exception) { 3000L } finally { r.release() }
}

private fun time(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0); return "%02d:%02d".format(s / 60, s % 60)
}
