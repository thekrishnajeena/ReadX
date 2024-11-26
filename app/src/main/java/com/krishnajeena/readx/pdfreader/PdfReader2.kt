package com.krishnajeena.readx.pdfreader

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

@Composable
fun PDFReader2(file: File) {
    val pdfRenderer = remember(file) { PdfDocumentRenderer(file) }

    DisposableEffect(Unit) {
        onDispose { pdfRenderer.close() }
    }

    val scrollState = rememberLazyListState()
    val zoomLevel = remember { mutableStateOf(1f) }
    LazyColumn(state = scrollState, modifier = Modifier.fillMaxSize()) {
        items(pdfRenderer.pageCount) { index ->


            PDFPage(
                pdfRenderer = pdfRenderer,
                pageIndex = index,
                scrollState = scrollState, zoomLevel
            )
        }
    }
}

@Composable
fun PDFPage(
    pdfRenderer: PdfDocumentRenderer,
    pageIndex: Int,
    scrollState: LazyListState,
    zoomLevel: MutableState<Float>
) {
    //val pageContent by pdfRenderer.getVisibleContent(pageIndex, scrollState).collectAsState()
    var pageContent = pdfRenderer.getVisibleContent(pageIndex, scrollState, zoomLevel.value)

    pageContent?.let { bitmap ->
        Box(modifier = Modifier.fillMaxSize()) {
            pageContent.collectAsState().value?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Page $pageIndex",
                    contentScale = ContentScale.FillWidth
                )
            }
        }
    }
}class PdfDocumentRenderer(file: File) {
    private val fileDescriptor: ParcelFileDescriptor =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val pdfRenderer = PdfRenderer(fileDescriptor)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    val pageCount: Int = pdfRenderer.pageCount
    private val visibleContentCache = mutableMapOf<Int, MutableStateFlow<Bitmap?>>()

    init {
        repeat(pageCount) { index ->
            visibleContentCache[index] = MutableStateFlow(null)
        }
    }

    fun getVisibleContent(pageIndex: Int, scrollState: LazyListState, zoomLevel: Float): StateFlow<Bitmap?> {
        val stateFlow = visibleContentCache[pageIndex]
            ?: throw IllegalArgumentException("Invalid page index")

        renderVisiblePart(pageIndex, stateFlow, scrollState, zoomLevel)
        return stateFlow
    }

    private fun renderVisiblePart(
        pageIndex: Int,
        stateFlow: MutableStateFlow<Bitmap?>,
        scrollState: LazyListState,
        zoomLevel: Float
    ) {
        scope.launch {
            // Ensure the previous page is closed before opening a new one
            val page = pdfRenderer.openPage(pageIndex)

            try {
                // Calculate the visible area (adjust based on zoom level)
                val visibleHeight = 800 * zoomLevel // Adjusting height based on zoom
                val visibleWidth = (scrollState.layoutInfo.visibleItemsInfo[0].size) * zoomLevel
                val viewport = Rect(
                    0,
                    (scrollState.firstVisibleItemIndex * visibleHeight).toInt(),
                    (page.width * zoomLevel).toInt(),
                    visibleHeight.toInt()
                )

                // Scale down the image resolution when zoomed out
                val scaledWidth = (page.width * zoomLevel).toInt()
                val scaledHeight = (visibleHeight * zoomLevel).toInt()

                val bitmap = Bitmap.createBitmap(
                    scaledWidth,
                    scaledHeight,
                    Bitmap.Config.ARGB_8888
                )

                // Render the portion of the page that is visible at the current zoom level
                page.render(bitmap, viewport, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                // Emit the rendered bitmap to the StateFlow
                stateFlow.emit(bitmap)
            } catch (e: Exception) {
                // Log rendering issues
                e.printStackTrace()
            } finally {
                // Ensure page is closed after rendering
                page.close()
            }
        }
    }

    fun close() {
        // Cancel any ongoing tasks and close resources properly
        scope.cancel()
        pdfRenderer.close()
        fileDescriptor.close()

        // Release resources in the cache
        visibleContentCache.values.forEach { it.value?.recycle() }
    }
}
