package com.krishnajeena.readx.pdfreader

import android.graphics.Bitmap
import android.graphics.Bitmap.createBitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable
import java.io.File


@Composable
fun PDFReader(file: File) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        val pdfRender = remember(file) {
            PdfRender(
                fileDescriptor = ParcelFileDescriptor.open(
                    file,
                    ParcelFileDescriptor.MODE_READ_ONLY
                )
            )
        }

        DisposableEffect(Unit) {
            onDispose { pdfRender.close() }
        }

        val zoomState = rememberZoomState()

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .zoomable(zoomState)
        ) {
            items(count = pdfRender.pageCount) { index ->
                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val page = pdfRender.pageLists[index]

                    LaunchedEffect(key1 = zoomState.scale) {
                        page.load(zoomState.scale)
//                        onDispose {
//                            page.recycle()
//                        }
                    }

                    // Collect the bitmap content and display it
                    page.pageContent.collectAsState().value?.let { bitmap ->
                        if (!bitmap.isRecycled) { // Additional check to ensure bitmap isn't recycled
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "PDF page number: $index",
                                modifier = Modifier.fillMaxWidth(),
                                contentScale = ContentScale.FillWidth
                            )
                        }
                    } ?: Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(page.heightByWidth(constraints.maxWidth).dp)
                    )
                }
            }
        }
    }
}

internal class PdfRender(
    private val fileDescriptor: ParcelFileDescriptor
) {
    private val pdfRenderer = PdfRenderer(fileDescriptor)
    val pageCount get() = pdfRenderer.pageCount
    private val mutex = Mutex()
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    val pageLists: List<Page> = List(pdfRenderer.pageCount) { index ->
        Page(index, pdfRenderer, coroutineScope, mutex)
    }

    fun close() {
        pageLists.forEach { it.recycle() }
        pdfRenderer.close()
        fileDescriptor.close()
    }

    class Page(
        private val index: Int,
        private val pdfRenderer: PdfRenderer,
        private val coroutineScope: CoroutineScope,
        private val mutex: Mutex
    ) {
        var isLoaded = false
        var job: Job? = null
        val dimension = pdfRenderer.openPage(index).use { it.width to it.height }
        val pageContent = MutableStateFlow<Bitmap?>(null)

        fun load(scale: Float) {
            job?.cancel() // Cancel any ongoing rendering before starting a new one
            job = coroutineScope.launch {
                mutex.withLock {
                    val bitmap = renderPage(scale)
                    isLoaded = true
                    pageContent.emit(bitmap)
                }
            }
        }

        private fun renderPage(scale: Float): Bitmap {
            pdfRenderer.openPage(index).use { page ->
                val width = (page.width * scale).toInt()
                val height = (page.height * scale).toInt()
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                return bitmap
            }
        }

        fun recycle() {
            job?.cancel() // Ensure any pending rendering job is canceled
            val bitmap = pageContent.value
            pageContent.tryEmit(null) // Set the flow to null before recycling
            bitmap?.takeIf { !it.isRecycled }?.recycle() // Check to prevent double recycling
        }

        fun heightByWidth(width: Int): Int {
            val ratio = dimension.first.toFloat() / dimension.second
            return (width / ratio).toInt()
        }
    }
}
