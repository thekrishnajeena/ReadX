package com.krishnajeena.readx

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*

import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.atwa.filepicker.core.FilePicker
import com.krishnajeena.readx.pdfreader.PDFReader
import com.krishnajeena.readx.ui.theme.ReadXTheme
import java.io.File

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()


        val ac = this
        val fp = FilePicker.getInstance(ac)
        setContent {
            ReadXTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->

                    var isTrue by remember{mutableStateOf(false)}
var fi by remember { mutableStateOf("") }
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                   if(isTrue){
                            PDFReader(file = File(fi))
                        }

                   else{
                       Button(
                           onClick = {
                               fp.pickPdf { fil ->
                                   isTrue = true
                                   fi = fil?.file.toString()
                               }
                           },
                           modifier = Modifier
                       ) {
                           Text("Hello")
                       }
                   }

                    }

                }
            }
        }
    }

}




@Composable
fun PdfPageView(file: File, pageIndex: Int) {
    var scale by remember { mutableStateOf(1f) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(file, pageIndex, scale) {
        bitmap = renderPdfPage(file, pageIndex, scale)
    }

    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        scale *= zoom
                    }
                }
        )
    }
}

fun renderPdfPage(file: File, pageIndex: Int, scale: Float): Bitmap? {
    val pdfRenderer = PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY))
    val page = pdfRenderer.openPage(pageIndex)

    val width = (page.width * scale).toInt()
    val height = (page.height * scale).toInt()

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
    page.close()
    pdfRenderer.close()

    return bitmap
}
