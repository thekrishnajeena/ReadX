package com.krishnajeena.readx

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var files: SnapshotStateList<Uri>

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            refreshFileList("pdf")
        } else {
            showToast("Permission denied")
        }
    }

    private val resultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (hasManageExternalStoragePermission()) {
            refreshFileList("pdf")
        } else {
            showToast("Permission denied")
        }
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun refreshFileList(type: String) {
        files.clear()
        files.addAll(listFiles(this, type))
        Log.i("Refresh:::::::", files.toString())
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!hasManageExternalStoragePermission()) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                resultLauncher.launch(intent)
            } else {
                Log.i("Refresh:::::::", "List)")
                refreshFileList("pdf")
            }
        } else {
            requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun hasManageExternalStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashscreen = installSplashScreen()
        var keepSplashScreen = true
        super.onCreate(savedInstanceState)

        splashscreen.setKeepOnScreenCondition { keepSplashScreen }
        lifecycleScope.launch {
            delay(500)
            keepSplashScreen = false
        }

        enableEdgeToEdge()

        files = mutableStateListOf()

        setContent {
            ReadXTheme {
                var isSearchMode by remember { mutableStateOf(false) }
                var searchQuery by remember { mutableStateOf("") }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                AnimatedContent(targetState = isSearchMode) { targetState ->
                                    if (targetState) {
                                        TextField(
                                            value = searchQuery,
                                            onValueChange = { searchQuery = it },
                                            placeholder = { Text("Search...") },
                                            singleLine = true,
                                            modifier = Modifier
                                                .fillMaxWidth(0.9f)
                                                .height(55.dp)
                                                .clip(RoundedCornerShape(15.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                                .padding(horizontal = 8.dp),
                                            colors = TextFieldDefaults.textFieldColors(
                                                focusedIndicatorColor = Color.Transparent,
                                                unfocusedIndicatorColor = Color.Transparent
                                            ),
                                            textStyle = TextStyle(fontSize = 14.sp),
                                            leadingIcon = {
                                                IconButton(onClick = {
                                                    isSearchMode = false
                                                    searchQuery = ""
                                                }) {
                                                    Icon(Icons.Default.Close, contentDescription = "Close")
                                                }
                                            }
                                        )
                                    } else {
                                        Text("ReadX", textAlign = TextAlign.Start)
                                    }
                                }
                            },
                            actions = {
                                if (!isSearchMode) {
                                    IconButton(onClick = { isSearchMode = true }) {
                                        Icon(imageVector = Icons.Filled.Search, contentDescription = "Search")
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = TopAppBarDefaults.mediumTopAppBarColors()
                        )
                    },
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->

                    val navController = rememberNavController()
                    val context = LocalContext.current

                    // Request permission on first load
                    LaunchedEffect(Unit) {
                        requestStoragePermission()
                    }

                    NavHost(navController, "start") {
                        composable("start") {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(innerPadding)
                                ) {
                                    FileList(files, context, navController) {
                                        isSearchMode = false
                                        searchQuery = ""
                                    }
                                }
                            }
                        }

                        composable("readX/{uri}",
                            arguments = listOf(navArgument("uri") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val uri = backStackEntry.arguments?.getString("uri")?.let(Uri::parse)
                            uri?.let {
                                getFilePathFromUri(context, it)?.let { path ->
                                    isSearchMode = false
                                    searchQuery = ""
                                    PDFReader(File(path))
                                }
                            } ?: run {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    Text("Uri is null!")
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


@Composable
fun FileList(files: List<Uri>, context: Context, navController: NavController, onItemClick: () -> Unit) {
    Log.i("TAG::::::", files.toString())
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
                .clickable {
                    onItemClick
                    navController.navigate("readX/${Uri.encode(fileUri.toString())}")
                          },
                shape = RoundedCornerShape(5.dp),
                elevation = CardDefaults.elevatedCardElevation(10.dp)) {
                Text(text = fileName ?: "Unknown File", modifier = Modifier.padding(8.dp))
            }
        }
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
fun listFiles(context: Context, type: String): List<Uri> {
    val uris = mutableListOf<Uri>()
    val contentUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)

    val projection = arrayOf(
        MediaStore.Files.FileColumns._ID,
        MediaStore.Files.FileColumns.DISPLAY_NAME,
        MediaStore.Files.FileColumns.MIME_TYPE
    )

    val selection = "${MediaStore.Files.FileColumns.MIME_TYPE} = ?"
    val selectionArgs = arrayOf("application/pdf")

    val cursor = context.contentResolver.query(
        contentUri,
        projection,
        selection,
        selectionArgs,
        null
    )

    cursor?.use {
        val idColumn = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)

        while (it.moveToNext()) {
            val id = it.getLong(idColumn)
            val uri = ContentUris.withAppendedId(contentUri, id)
            uris.add(uri)
        }
    }

    return uris
}
