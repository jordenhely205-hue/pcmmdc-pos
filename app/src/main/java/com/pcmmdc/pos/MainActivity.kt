package com.pcmmdc.pos

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

data class AnimalCategory(val nameUrdu: String, val rate: Long)

data class SaleRecord(
    val receiptNo: String,
    val category: String,
    val qty: Int,
    val base: Long,
    val pst: Long,
    val total: Long,
    val dateTime: String
)

class MainActivity : ComponentActivity() {

    private var outputStream: OutputStream? = null
    private val salesList = mutableStateListOf<SaleRecord>()

    private val categories = listOf(
        AnimalCategory("بڑا جانور", 1500L),
        AnimalCategory("چھوٹا جانور", 500L),
        AnimalCategory("بیل / گائے / بھینس", 1500L),
        AnimalCategory("بکرا / چھترا", 500L),
        AnimalCategory("بچھڑا / کٹا", 1000L),
        AnimalCategory("اونٹ", 2000L),
        AnimalCategory("گھوڑا / خچر", 1000L),
        AnimalCategory("موٹر بائیک", 50L)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MainAppFlow()
            }
        }
    }

    @Composable
    fun MainAppFlow() {
        var isLoggedIn by remember { mutableStateOf(false) }

        if (!isLoggedIn) {
            LoginScreen(onLoginSuccess = { isLoggedIn = true })
        } else {
            POSScreen()
        }
    }

    @Composable
    fun LoginScreen(onLoginSuccess: () -> Unit) {
        var username by remember { mutableStateOf("yasir") }
        var password by remember { mutableStateOf("1234") }
        var error by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(48.dp))
                    Text("PCMMDC لاگ ان", fontSize = 22.sp, fontWeight = FontWeight.Bold)

                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("آپریٹر یوزر نیم") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("پاس ورڈ") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (error) {
                        Text("غلط صارف یا پاس ورڈ", color = ComposeColor.Red, fontSize = 14.sp)
                    }

                    Button(
                        onClick = {
                            if (username.isNotBlank() && password == "1234") {
                                onLoginSuccess()
                            } else {
                                error = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        Text("لاگ ان کریں", fontSize = 17.sp)
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    @Composable
    fun POSScreen() {
        var selectedCat by remember { mutableStateOf(categories[0]) }
        var quantity by remember { mutableIntStateOf(1) }
        var receiptNo by remember { mutableStateOf("260829170356296") }

        val defaultDateTime = SimpleDateFormat("dd/MM/yyyy hh:mm:ss a", Locale.US).format(Date())
        var manualDateTime by remember { mutableStateOf(defaultDateTime) }

        var connectedDeviceName by remember { mutableStateOf<String?>(null) }
        var showDeviceDialog by remember { mutableStateOf(false) }
        var showReportDialog by remember { mutableStateOf(false) }
        var pairedDevices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }

        val basePrice = selectedCat.rate * quantity
        val pstTax = Math.round(basePrice * 0.16)
        val grandTotal = basePrice + pstTax

        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { perms ->
            if (perms.values.all { it }) {
                openDevicePicker { devs -> pairedDevices = devs; showDeviceDialog = true }
            } else {
                Toast.makeText(this@MainActivity, "بلوٹوتھ پرمیشن ضروری ہے!", Toast.LENGTH_SHORT).show()
            }
        }

        Scaffold(
            topBar = {
                Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { showReportDialog = true }) {
                            Icon(Icons.Default.Assessment, contentDescription = "Record")
                        }
                        Text("PCMMDC POS", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Button(onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                                    permissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN))
                                } else {
                                    openDevicePicker { devs -> pairedDevices = devs; showDeviceDialog = true }
                                }
                            } else {
                                openDevicePicker { devs -> pairedDevices = devs; showDeviceDialog = true }
                            }
                        }) {
                            Text(connectedDeviceName ?: "پرنٹر جوڑیں")
                        }
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(14.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("جانور کی قسم منتخب کریں:", fontWeight = FontWeight.Bold)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.height(230.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories) { cat ->
                        OutlinedButton(
                            onClick = { selectedCat = cat },
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (selectedCat == cat) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(cat.nameUrdu, fontWeight = FontWeight.Bold)
                                Text("Rs. ${cat.rate}")
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("تعداد (Quantity):", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { if (quantity > 1) quantity-- }) { Text("-") }
                        Text("$quantity", modifier = Modifier.padding(horizontal = 16.dp), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Button(onClick = { quantity++ }) { Text("+") }
                    }
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("بنیادی رقم: Rs. $basePrice")
                        Text("PST ٹیکس (16%): Rs. $pstTax")
                        Divider(modifier = Modifier.padding(vertical = 4.dp))
                        Text("کل رقم: Rs. $grandTotal", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                }

                OutlinedTextField(
                    value = receiptNo,
                    onValueChange = { receiptNo = it },
                    label = { Text("رسید نمبر (Receipt No)") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = manualDateTime,
                    onValueChange = { manualDateTime = it },
                    label = { Text("تاریخ و وقت (Manual Date & Time)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        lifecycleScope.launch {
                            printSlip(selectedCat.nameUrdu, quantity, selectedCat.rate, basePrice, pstTax, grandTotal, receiptNo, manualDateTime)
                            salesList.add(
                                SaleRecord(receiptNo, selectedCat.nameUrdu, quantity, basePrice, pstTax, grandTotal, manualDateTime)
                            )
                            receiptNo = (receiptNo.toLongOrNull()?.plus(1) ?: receiptNo).toString()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Icon(Icons.Default.Print, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("پرچی پرنٹ کریں", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (showReportDialog) {
            val totalSlips = salesList.size
            val totalAnimals = salesList.sumOf { it.qty }
            val totalAmount = salesList.sumOf { it.total }

            Dialog(onDismissRequest = { showReportDialog = false }) {
                Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("روزانہ سیل ریکارڈ (Daily Summary)", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Divider()
                        Text("کل پرچیاں کٹیں: $totalSlips")
                        Text("کل جانور: $totalAnimals")
                        Text("کل وصول شدہ رقم: Rs. $totalAmount", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(onClick = { showReportDialog = false }, modifier = Modifier.fillMaxWidth()) {
                            Text("بند کریں")
                        }
                    }
                }
            }
        }

        if (showDeviceDialog) {
            Dialog(onDismissRequest = { showDeviceDialog = false }) {
                Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("بلوٹوتھ پرنٹر منتخب کریں", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        if (pairedDevices.isEmpty()) {
                            Text("کوئی پیئرڈ ڈیوائس نہیں ملی۔ فون کی بلوٹوتھ میں جا کر پہلے پرنٹر پیئر کریں۔")
                        } else {
                            pairedDevices.forEach { dev ->
                                TextButton(
                                    onClick = {
                                        connectToPrinter(dev) { name -> connectedDeviceName = name }
                                        showDeviceDialog = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(dev.name ?: dev.address, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun openDevicePicker(onDevices: (List<BluetoothDevice>) -> Unit) {
        try {
            val bm = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val bonded = bm.adapter?.bondedDevices?.toList() ?: emptyList()
            onDevices(bonded)
        } catch (e: Exception) {
            Toast.makeText(this, "ایرر: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToPrinter(device: BluetoothDevice, onConnected: (String) -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                outputStream?.close()
                val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
                val socket = device.createRfcommSocketToServiceRecord(uuid)
                socket.connect()
                outputStream = socket.outputStream
                withContext(Dispatchers.Main) {
                    onConnected(device.name ?: "Connected")
                    Toast.makeText(this@MainActivity, "پرنٹر کنیکٹ ہو گیا!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "کنکشن فیل: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private suspend fun printSlip(
        catName: String, qty: Int, unitRate: Long, basePrice: Long,
        pst: Long, total: Long, rNo: String, dateTimeStr: String
    ) = withContext(Dispatchers.Default) {
        if (outputStream == null) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "پرنٹر منسلک نہیں ہے!", Toast.LENGTH_SHORT).show()
            }
            return@withContext
        }

        // Height set to 1800 for true long slip spacing
        val bmp = Bitmap.createBitmap(384, 1800, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 21f
        }

        val nastaleeq = try {
            Typeface.createFromAsset(assets, "fonts/jameel_noori_nastaleeq.ttf")
        } catch (_: Throwable) {
            Typeface.DEFAULT_BOLD
        }

        var y = 40f

        // Draw Uploaded Logo (logo.png from res/drawable)
        val logoResId = resources.getIdentifier("logo", "drawable", packageName)
        if (logoResId != 0) {
            val originalBmp = BitmapFactory.decodeResource(resources, logoResId)
            if (originalBmp != null) {
                val size = 110
                val scaled = Bitmap.createScaledBitmap(originalBmp, size, size, true)
                canvas.drawBitmap(scaled, (384f - size) / 2f, y, null)
                y += size + 35f
            }
        } else {
            y += 40f
        }

        // Header Title
        paint.typeface = nastaleeq
        paint.textSize = 22f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("پنجاب کیٹل مارکیٹ مینجمنٹ اینڈ ڈویلپمنٹ کمپنی", 192f, y, paint)
        y += 65f

        paint.textSize = 19f
        fun drawRow(label: String, value: String) {
            paint.typeface = nastaleeq
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(label, 370f, y, paint)
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText(value, 14f, y, paint)
            y += 50f
        }

        drawRow("ڈویژن", "فیصل آباد")
        drawRow("مارکیٹ", "ماڈل مویشی منڈی جھنگ سٹی")
        drawRow("نام ٹھیکیدار", "محمد اسماعیل")
        drawRow("نام آپریٹر", "M Yasir Hameed")

        y += 20f
        paint.typeface = nastaleeq
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("رسید نمبر", 192f, y, paint)
        y += 38f
        paint.typeface = Typeface.MONOSPACE
        paint.textSize = 20f
        canvas.drawText(rNo, 192f, y, paint)
        y += 48f

        paint.typeface = nastaleeq
        paint.textSize = 19f
        canvas.drawText("تاریخ و وقت", 192f, y, paint)
        y += 38f
        paint.typeface = Typeface.MONOSPACE
        paint.textSize = 19f
        canvas.drawText(dateTimeStr, 192f, y, paint)
        y += 45f

        fun drawDashedLine(currentY: Float) {
            val dashPaint = Paint().apply {
                color = Color.BLACK
                style = Paint.Style.STROKE
                strokeWidth = 2f
                pathEffect = DashPathEffect(floatArrayOf(6f, 6f), 0f)
            }
            canvas.drawLine(14f, currentY, 370f, currentY, dashPaint)
        }

        drawDashedLine(y)
        y += 45f

        paint.typeface = nastaleeq
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("فیس رسید", 192f, y, paint)
        y += 45f

        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("فیس کی قسم", 370f, y, paint)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("تعداد", 245f, y, paint)
        canvas.drawText("یونٹ", 160f, y, paint)
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("قیمت", 14f, y, paint)
        y += 30f

        drawDashedLine(y)
        y += 45f

        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(catName, 370f, y, paint)
        paint.typeface = Typeface.MONOSPACE
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("$qty", 245f, y, paint)
        canvas.drawText("$unitRate", 160f, y, paint)
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("$basePrice", 14f, y, paint)
        y += 45f

        drawDashedLine(y)
        y += 45f

        paint.typeface = nastaleeq
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("PST(16%)", 370f, y, paint)
        paint.typeface = Typeface.MONOSPACE
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("$pst", 14f, y, paint)
        y += 50f

        paint.typeface = nastaleeq
        paint.textSize = 23f
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("کل", 370f, y, paint)
        paint.typeface = Typeface.MONOSPACE
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("$total", 14f, y, paint)
        y += 55f

        drawDashedLine(y)
        y += 50f

        paint.typeface = nastaleeq
        paint.textSize = 19f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("جاری کردہ توسط", 192f, y, paint)
        y += 42f
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textSize = 20f
        canvas.drawText("M Yasir Hameed", 192f, y, paint)
        y += 55f

        paint.typeface = nastaleeq
        paint.textSize = 24f
        canvas.drawText("اداشدہ", 192f, y, paint)
        y += 60f

        paint.textSize = 17f
        canvas.drawText("1233 : ہیلپ لائن", 192f, y, paint)
        y += 40f
        canvas.drawText("+92 323 1233000 : واٹس ایپ", 192f, y, paint)
        y += 40f
        canvas.drawText("31.215677, 72.355752 : GPS مقام", 192f, y, paint)
        y += 48f

        canvas.drawText("شکریہ", 192f, y, paint)
        y += 45f

        paint.typeface = Typeface.MONOSPACE
        canvas.drawText("Powered by PCMMDC", 192f, y, paint)
        y += 65f

        // ESC/POS Raster Print
        val cropped = Bitmap.createBitmap(bmp, 0, 0, 384, y.toInt())
        val stream = ByteArrayOutputStream()
        stream.write(byteArrayOf(0x1B, 0x40))
        stream.write(byteArrayOf(0x1B, 0x33, 0x00))

        val widthBytes = 48
        val height = cropped.height
        val xL = (widthBytes and 0xFF).toByte()
        val xH = ((widthBytes shr 8) and 0xFF).toByte()
        val yL = (height and 0xFF).toByte()
        val yH = ((height shr 8) and 0xFF).toByte()

        stream.write(byteArrayOf(0x1D, 0x76, 0x30, 0x00, xL, xH, yL, yH))

        for (row in 0 until height) {
            for (colByte in 0 until widthBytes) {
                var slice = 0
                for (b in 0 until 8) {
                    val px = colByte * 8 + b
                    if (px < 384) {
                        val pixel = cropped.getPixel(px, row)
                        val lum = (0.299 * Color.red(pixel) + 0.587 * Color.green(pixel) + 0.114 * Color.blue(pixel)).toInt()
                        if (lum < 140) {
                            slice = slice or (1 shl (7 - b))
                        }
                    }
                }
                stream.write(slice)
            }
        }
        stream.write(byteArrayOf(0x1B, 0x64, 0x04))

        try {
            outputStream?.write(stream.toByteArray())
            outputStream?.flush()
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "پرچی پرنٹ ہو گئی!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "پرنٹ فیل: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
