package com.krishnajeena.readx

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*

import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.atwa.filepicker.core.FilePicker
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.krishnajeena.readx.pdfreader.PDFReader
import com.krishnajeena.readx.ui.theme.ReadXTheme
import org.bouncycastle.oer.its.ieee1609dot2.basetypes.Elevation
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

                    val navController = rememberNavController()
                    var isTrue by remember{mutableStateOf(false)}

                    var fi by remember { mutableStateOf(File("")) }
                    val context = LocalContext.current


                    NavHost(navController, "start"){

                        composable("start"){
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {

//                                if(isTrue){
//                                    navController.navigate("readX")
//                                }
//
//                                else{
//                       Button(
//                           onClick = {
//                               fp.pickPdf { fil ->
//                                   isTrue = true
//                                   fi = fil?.file.toString()
//                               }
//                           },
//                           modifier = Modifier
//                       ) {
//                           Text("Hello")
//                       }

                                    Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                                        var files by remember {mutableStateOf<List<Uri>>(emptyList())}
                                        //   RequestPermission { files = listFiles(context, "pdf") }

                                        if(!Environment.isExternalStorageManager()) {
                                            requestAllFilesAccessPermission(context = context) {
                                                  files = listFiles(context, "pdf")
                                            }
                                        }
                                        files = listFiles(context, "pdf")
                                        FileList(files, context, navController)
                                    }
                                }

                          //  }

                        }

                        composable("readX/{uri}",
                            arguments = listOf(navArgument("uri") { type = NavType.StringType })){ backStackEntry ->
                            val uriString = backStackEntry.arguments?.getString("uri")
                            val uri = uriString?.let { Uri.parse(it) }
                            if (uri != null) {
                               getFilePathFromUri(context, uri)?.let {
                                    PDFReader   (
                                        File(it)
                                    )
                                }
                            }
                            else {
                                Box(modifier = Modifier.fillMaxSize()){
                                    Text("Uri null!")
                                }
                            }
                        }

                    }


                }
            }
        }
    }

}
fun getFilePathFromUri(context: Context, uri: Uri): String? {
    var filePath: String? = null
    if ("content".equals(uri.scheme, ignoreCase = true)) {
        val projection = arrayOf(MediaStore.Files.FileColumns.DATA)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
            if (cursor.moveToFirst()) {
                filePath = cursor.getString(columnIndex)
            }
        }
    } else if ("file".equals(uri.scheme, ignoreCase = true)) {
        filePath = uri.path
    }
    return filePath
}


fun copyContentUriToFile(context: Context, contentUri: Uri, destinationFile: File): File? {
    return try {
        context.contentResolver.openInputStream(contentUri)?.use { inputStream ->
            destinationFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        destinationFile
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}


@Composable
fun FileList(files: List<Uri>, context: Context, navController : NavController) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(files) { fileUri ->
            val fileName = remember(fileUri) {
                val cursor = context.contentResolver.query(fileUri, arrayOf(MediaStore.Files.FileColumns.DISPLAY_NAME), null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        it.getString(it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME))
                    } else null
                } ?: "Unknown File"
            }

            Card(modifier = Modifier.fillMaxWidth().padding(5.dp).height(60.dp)
                .clickable { navController.navigate("readX/${Uri.encode(fileUri.toString())}")  },
                shape = RoundedCornerShape(5.dp),
                elevation = CardDefaults.elevatedCardElevation(10.dp)) {
                Text(text = fileName ?: "Unknown File", modifier = Modifier.padding(8.dp))
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

fun listFiles(context: Context, fileType: String): List<Uri> {
    val files = mutableListOf<Uri>()

    // Define the file MIME type to filter
    val mimeType = when (fileType.lowercase()) {
        "pdf" -> "application/pdf"
        "image" -> "image/*"
        "text" -> "text/plain"
        else -> "*/*"
    }

    // Query MediaStore for files
    val projection = arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.DISPLAY_NAME)
    val selection = "${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ?"
    val selectionArgs = arrayOf(mimeType)
    val uri = MediaStore.Files.getContentUri("external")

    context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)
            val contentUri = ContentUris.withAppendedId(uri, id)
            files.add(contentUri)
        }
    }

    return files
}

@RequiresApi(Build.VERSION_CODES.R)
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun RequestPermission(onGranted: () -> Unit) {
    val permissionState = rememberPermissionState(permission = Manifest.permission.MANAGE_EXTERNAL_STORAGE)

    LaunchedEffect(key1 = permissionState.status) {
        if (permissionState.status.isGranted) {
            onGranted()
        } else {
            permissionState.launchPermissionRequest()
        }
    }

    if (!permissionState.status.isGranted) {
        Text(text = "Please grant storage permission")
    }
}

fun requestAllFilesAccessPermission(context: Context, onGranted: () -> Unit) {
    if (!Environment.isExternalStorageManager()) {
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    } else {
        // Permission already granted
        onGranted()
        Toast.makeText(context, "All Files Access Granted", Toast.LENGTH_SHORT).show()
    }
}
