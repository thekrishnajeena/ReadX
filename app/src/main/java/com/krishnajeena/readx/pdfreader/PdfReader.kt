package com.krishnajeena.readx.pdfreader

import android.content.ContentValues.TAG
import android.graphics.Bitmap
import android.graphics.Bitmap.createBitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
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
    PDFBoxResourceLoader.init(LocalContext.current)

    // Remembering the PdfRender instance for lazy loading
    val pdfRender = remember(file) {
        PdfRender(
            fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        )
    }

    DisposableEffect(Unit) {
        onDispose { pdfRender.close() }
    }

    // Remember zoom and selection states
    val zoomState = rememberZoomState()
    val selectionState = remember { mutableStateOf<Selection?>(null) }
    val scrollState = rememberLazyListState()

    // Handling touch gestures for selection
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            state = scrollState,
            modifier = Modifier
                .fillMaxSize()
                .zoomable(zoomState)
                .pointerInput(Unit) {
                    // Detect long press for starting selection
                    detectTapGestures(
                        onPress = { offset ->
                            // Start of long press to begin selection
                            val pressStartTime = System.currentTimeMillis()
                            awaitPointerEventScope {
                                val event = awaitPointerEvent()
                                // Wait for a long press (500 ms threshold)
                                if (System.currentTimeMillis() - pressStartTime >= 500) {
                                    // Mark the start of selection
                                    selectionState.value = Selection(startX = offset.x, startY = offset.y)
                                }
                            }
                        }
                    )
                    // Handle drag gesture to extend the selection
                    detectDragGestures { _, dragAmount ->
                        selectionState.value?.let {
                            // Update the end points of the selection based on drag
                            val newEndX = it.endX + dragAmount.x
                            val newEndY = it.endY + dragAmount.y
                            selectionState.value = it.copy(endX = newEndX, endY = newEndY)
                        }
                    }
                }
        ) {
            items(count = pdfRender.pageCount) { index ->
                val page = pdfRender.pageLists[index]

                LaunchedEffect(key1 = zoomState.scale) {
                    page.load(zoomState.scale)
                }

                // Collect bitmap content and display
                page.pageContent.collectAsState().value?.let { bitmap ->
                    if (!bitmap.isRecycled) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "PDF page number: $index",
                            modifier = Modifier.fillMaxWidth().clickable {
                                Log.i(TAG, "${extractTextFromPage(file, index + 1)}") }
                                .drawWithContent {
                                    // Draw the selection rectangle
                                    selectionState.value?.let { selection ->
                                        val startX = selection.startX
                                        val startY = selection.startY
                                        val endX = selection.endX
                                        val endY = selection.endY
                                        drawRect(
                                            color = Color.Blue.copy(alpha = 0.5f),
                                            size = Size(endX - startX, endY - startY),
                                            topLeft = Offset(startX, startY)
                                        )
                                    }
                                    // Draw the content of the page
                                    drawContent()
                                },
                            contentScale = ContentScale.FillWidth
                        )
                    }
                }
            }
        }
    }
}

data class Selection(
    val startX: Float,
    val startY: Float,
    val endX: Float = startX,
    val endY: Float = startY
)



// Your PdfRender class should remain the same as in the original code

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


fun extractTextFromPage(pdfFile: File, pageNumber: Int): String {

    var extractedText = ""

    PDDocument.load(pdfFile).use { document ->
        if (pageNumber <= document.numberOfPages && pageNumber > 0) {
            val pdfStripper = PDFTextStripper()
            pdfStripper.startPage = pageNumber
            pdfStripper.endPage = pageNumber
            extractedText = pdfStripper.getText(document)

        } else {
            throw IllegalArgumentException("Invalid page number.")
        }
    }
    return extractedText
}