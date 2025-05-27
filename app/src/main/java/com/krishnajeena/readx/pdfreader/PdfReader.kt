package com.krishnajeena.readx.pdfreader

import android.content.ContentValues.TAG
import android.graphics.Bitmap
import android.graphics.Bitmap.createBitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.engawapg.lib.zoomable.ZoomState
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable
import org.bouncycastle.asn1.cms.Time
import java.io.File
@Composable
fun PDFReader(file: File) {
    PDFBoxResourceLoader.init(LocalContext.current)

    // Initialize PdfRender with file descriptor
    val pdfRender = remember(file) {
        PdfRender(
            fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        )
    }

    DisposableEffect(Unit) {
        onDispose { pdfRender.close() }
    }

    // Zoom and scroll states
    val zoomState = rememberZoomState()
    val scrollState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = scrollState,
            modifier = Modifier
                .fillMaxSize()
                .zoomable(zoomState)
        ) {
            items(pdfRender.pageCount, key = { index -> "${index}_${zoomState.scale}" }) { index ->
                val page = pdfRender.pageLists[index]
                val scale = zoomState.scale

                // Render each page
                PageContent(page, scale)
            }
        }

        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO){
                val start = System.currentTimeMillis()
                val coordinatesData = extractTextFromPage(file, 7)
                val end = System.currentTimeMillis()
                Log.i("TAG:Coor", "Data: ${coordinatesData.toString()}")
                Log.i("Time: ",  "extracted in ${end - start} ms")
            }
        }
    }
}

@Composable
private fun PageContent(page: PdfRender.Page, scale: Float) {
    LaunchedEffect(scale) {
        page.load(scale)
    }

    val bitmap by page.pageContent.collectAsState()

    bitmap?.let { bmp ->
        if (!bmp.isRecycled) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "PDF page",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(5.dp)
                    .background(Color.White),
                contentScale = ContentScale.FillWidth
            )
        }
    }
}

internal class PdfRender(
    private val fileDescriptor: ParcelFileDescriptor
) {
    private val pdfRenderer = PdfRenderer(fileDescriptor)
    val pageCount get() = pdfRenderer.pageCount

    // LRU Cache for Bitmaps
    private val bitmapCache = LruCache<String, Bitmap>(4 * 1024 * 1024) // 4MB cache size
    private val mutex = Mutex()
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    val pageLists: List<Page> = List(pdfRenderer.pageCount) { index ->
        Page(index, pdfRenderer, bitmapCache, coroutineScope, mutex)
    }

    fun close() {
        pageLists.forEach { it.recycle() }
        pdfRenderer.close()
        fileDescriptor.close()
    }

    class Page(
        private val index: Int,
        private val pdfRenderer: PdfRenderer,

        private val bitmapCache: LruCache<String, Bitmap>
        ,
        private val coroutineScope: CoroutineScope,
        private val mutex: Mutex
    ) {
        val pageContent = MutableStateFlow<Bitmap?>(null)
        private var renderJob: Job? = null

        fun load(scale: Float) {
            renderJob?.cancel()
            renderJob = coroutineScope.launch {
                mutex.withLock {
                    val cacheKey = "$index-${scale}"
                    val cachedBitmap = bitmapCache.get(cacheKey)
                    if (cachedBitmap != null) {
                        pageContent.emit(cachedBitmap)
                    } else {
                        val bitmap = renderPage(scale)
                        bitmapCache.put(cacheKey, bitmap)
                        pageContent.emit(bitmap)
                    }
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
            renderJob?.cancel()
            pageContent.value?.recycle()
            pageContent.tryEmit(null)
        }
    }
}


fun extractTextFromPage(pdfFile: File, pageNumber: Int): String {

    var extractedText = ""

    PDDocument.load(pdfFile).use { document ->
        if (pageNumber <= document.numberOfPages && pageNumber > 0) {
            val pdfStripper = CustomPDFTextStripper()
            pdfStripper.startPage = pageNumber
            pdfStripper.endPage = pageNumber
            extractedText = pdfStripper.getText(document)

        } else {
            throw IllegalArgumentException("Invalid page number.")
        }
    }
    return extractedText
}

fun extractTextWithCoordinates(pdfFile: File, pageNumber: Int): List<Triple<String, Float, Float>> {
    val textWithCoordinates = mutableListOf<Triple<String, Float, Float>>()

    PDDocument.load(pdfFile).use { document ->
        if (pageNumber < document.numberOfPages && pageNumber >= 0) {
            val stripper = object : PDFTextStripper() {
                override fun writeString(text: String, textPositions: List<TextPosition>) {
                    val x = textPositions.first().xDirAdj
                    val y = textPositions.first().yDirAdj
                    textWithCoordinates.add(Triple(text, x, y))
                }
            }
            stripper.startPage = pageNumber + 1
            stripper.endPage = pageNumber + 1
            stripper.getText(document)
        } else {
            throw IllegalArgumentException("Invalid page number.")
        }
    }
    return textWithCoordinates
}



@Composable
fun CustomToolbar(
    offset: IntOffset,
    onCustomAction: (String) -> Unit
) {
    Popup(offset = offset) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Button(onClick = { onCustomAction("Search") }) {
                Text("Search")
            }
            Button(onClick = { onCustomAction("Share") }) {
                Text("Share")
            }
            Button(onClick = { onCustomAction("Translate") }) {
                Text("Translate")
            }
        }
    }
}


