package com.viewfinder.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Size
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viewfinder.data.ProjectStore
import com.viewfinder.data.Shot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Full-screen grid of the current project's shots, newest first. */
@Composable
fun GalleryScreen(store: ProjectStore, onClose: () -> Unit) {
    val project = store.current
    // Grid keys must be unique; a shot referenced twice would otherwise crash the grid.
    val shots = store.shotsOf(project.id).distinctBy { it.uri }
    var open by remember { mutableStateOf<Shot?>(null) }

    BackHandler(onBack = onClose)
    Column(
        Modifier
            .fillMaxSize()
            .background(Mat)
            .clickable(enabled = false) {}
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(project.title, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("${shots.size} ${if (shots.size == 1) "shot" else "shots"}", color = Muted, fontSize = 11.sp)
            }
            IconPill(Icons.Close, onClick = onClose)
        }
        if (shots.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No shots in this project yet.", color = Muted, fontSize = 13.sp)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(180.dp),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(shots, key = { it.uri }) { shot ->
                    Thumbnail(shot, onMissing = { store.removeShot(project.id, shot.uri) }) { open = shot }
                }
            }
        }
    }

    open?.let { shot ->
        ShotViewer(
            shot,
            onDelete = { ctx ->
                if (!deleteShot(ctx, shot)) {
                    Toast.makeText(ctx, "Removed from project. The file stays in Pictures/viewfinder.", Toast.LENGTH_LONG).show()
                }
                store.removeShot(project.id, shot.uri)
                open = null
            },
            onClose = { open = null },
        )
    }
}

@Composable
private fun Thumbnail(shot: Shot, onMissing: () -> Unit, onClick: () -> Unit) {
    val context = LocalContext.current
    val thumb by produceState<Bitmap?>(null, shot.uri) {
        value = withContext(Dispatchers.IO) {
            try {
                context.contentResolver.loadThumbnail(Uri.parse(shot.uri), Size(480, 360), null)
            } catch (e: Exception) {
                null
            }
        }
        // The file was deleted outside the app: drop it from the project.
        if (value == null && !exists(context, shot.uri)) onMissing()
    }
    Column(Modifier.clip(Square).clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).background(Selected), contentAlignment = Alignment.Center) {
            thumb?.let {
                Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
        }
        Text(
            "Scene ${shot.scene}  ·  Shot ${shot.shot}",
            color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Medium,
        )
        Text(stamp(shot.time), color = Muted, fontSize = 10.sp)
    }
}

/** One shot full screen, with share and delete. */
@Composable
private fun ShotViewer(shot: Shot, onDelete: (Context) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    var confirmDelete by remember(shot.uri) { mutableStateOf(false) }
    val image by produceState<Bitmap?>(null, shot.uri) {
        value = withContext(Dispatchers.IO) { decodeScaled(context, Uri.parse(shot.uri), 2400) }
    }
    BackHandler(onBack = onClose)
    Row(
        Modifier
            .fillMaxSize()
            .background(Mat)
            .clickable(enabled = false) {}
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
            image?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
        }
        Column(Modifier.width(110.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            IconPill(Icons.Close, Modifier.fillMaxWidth(), onClick = onClose)
            Text("Scene ${shot.scene}  ·  Shot ${shot.shot}", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(stamp(shot.time), color = Muted, fontSize = 10.sp)
            Box(Modifier.weight(1f))
            Pill("Share", selected = false, modifier = Modifier.fillMaxWidth()) {
                val send = Intent(Intent.ACTION_SEND)
                    .setType("image/jpeg")
                    .putExtra(Intent.EXTRA_STREAM, Uri.parse(shot.uri))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(Intent.createChooser(send, null))
            }
            Pill(if (confirmDelete) "Confirm" else "Delete", selected = confirmDelete, modifier = Modifier.fillMaxWidth()) {
                if (confirmDelete) onDelete(context) else confirmDelete = true
            }
        }
    }
}

private fun stamp(time: Long) = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.UK).format(Date(time))

/** False only if the file is really gone; unreadable (e.g. not owned after a reinstall) counts as present. */
private fun exists(context: Context, uri: String): Boolean = try {
    context.contentResolver.openFileDescriptor(Uri.parse(uri), "r")?.use { true } ?: false
} catch (e: java.io.FileNotFoundException) {
    false
} catch (e: Exception) {
    true
}

/** Deletes the image file; returns false if Android refused (e.g. not owned after a reinstall). */
private fun deleteShot(context: Context, shot: Shot): Boolean = try {
    context.contentResolver.delete(Uri.parse(shot.uri), null, null) > 0
} catch (e: Exception) {
    false
}

private fun decodeScaled(context: Context, uri: Uri, maxSide: Int): Bitmap? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    }
} catch (e: Exception) {
    null
}
