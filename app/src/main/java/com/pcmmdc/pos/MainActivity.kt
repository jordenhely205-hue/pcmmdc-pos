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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
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
    val vehicleNo: String,
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
        AnimalCategory("موٹر بائیک", 50L),
        AnimalCategory("بڑا جانور", 1500L),
        AnimalCategory("چھوٹا جانور", 500L),
        AnimalCategory("بیل / گائے / بھینس", 1500L),
        AnimalCategory("بکرا / چھترا", 500L),
        AnimalCategory("بچھڑا / کٹا", 1000L),
        AnimalCategory("اونٹ", 2000L),
        AnimalCategory("گاڑی / لوڈر", 200L)
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
        var username by remember { mutableStateOf("Naeem409") }
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
        var vehicleNo by remember { mutableStateOf("M0861") }
        var contractorName by remember { mutableStateOf("Muhammad Ramzan and Company") }
        var operatorName by remember { mutableStateOf("Naeem409") }

        val defaultDateTime = SimpleDateFormat("dd-MMM-yyyy | hh:mm:ss a", Locale.US).format(Date())
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
                        OutlinedButton(onClick = { showReportDialog = true }) {
                            Text("روزانہ ریکارڈ")
                        }
                        Text("16cm Mandi POS", fontWeight = FontWeight.Bold, fontSize = 17.sp)
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
                Text("فیس کی قسم منتخب کریں:", fontWeight = FontWeight.Bold)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.height(210.dp),
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
                    value = vehicleNo,
                    onValueChange = { vehicleNo = it },
                    label = { Text("گاڑی نمبر (Vehicle No)") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = operatorName,
                    onValueChange = { operatorName = it },
                    label = { Text("جاری کردہ توسط (Operator Name)") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = manualDateTime,
                    onValueChange = { manualDateTime = it },
                    label = { Text("تاریخ و وقت (Date & Time)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        lifecycleScope.launch {
                            val txnId = "CHK-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                            printSlip(selectedCat.nameUrdu, vehicleNo, quantity, selectedCat.rate, basePrice, pstTax, grandTotal, manualDateTime, contractorName, operatorName, txnId)
                            salesList.add(
                                SaleRecord(txnId, vehicleNo, selectedCat.nameUrdu, quantity, basePrice, pstTax, grandTotal, manualDateTime)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text("🖨️  16cm پرچی پرنٹ کریں", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (showReportDialog) {
            val totalSlips = salesList.size
            val totalQty = salesList.sumOf { it.qty }
            val totalAmount = salesList.sumOf { it.total }

            Dialog(onDismissRequest = { showReportDialog = false }) {
                Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("روزانہ سیل ریکارڈ (Daily Summary)", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Divider()
                        Text("کل پرچیاں: $totalSlips")
                        Text("کل تعداد: $totalQty")
                        Text("کل رقم: Rs. $totalAmount", fontWeight = FontWeight.Bold, fontSize = 18.sp)
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
        catName: String, vehNo: String, qty: Int, unitRate: Long, basePrice: Long,
        pst: Long, total: Long, dateTimeStr: String, contractor: String, operatorName: String, txnId: String
    ) = withContext(Dispatchers.Default) {
        if (outputStream == null) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "پرنٹر منسلک نہیں ہے!", Toast.LENGTH_SHORT).show()
            }
            return@withContext
        }

        // Calibrated 16 cm total height (1320 px max)
        val bmp = Bitmap.createBitmap(384, 1320, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
        }

        val nastaleeq = try {
            Typeface.createFromAsset(assets, "fonts/jameel_noori_nastaleeq.ttf")
        } catch (_: Throwable) {
            Typeface.DEFAULT_BOLD
        }

        var y = 24f

        // 1. Logo
        val logoResId = resources.getIdentifier("logo", "drawable", packageName)
        if (logoResId != 0) {
            val originalBmp = BitmapFactory.decodeResource(resources, logoResId)
            if (originalBmp != null) {
                val size = 95
                val scaled = Bitmap.createScaledBitmap(originalBmp, size, size, true)
                canvas.drawBitmap(scaled, (384f - size) / 2f, y, null)
                y += size + 20f
            }
        } else {
            y += 25f
        }

        // 2. Header
        paint.typeface = nastaleeq
        paint.textSize = 23f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("پنجاب کیٹل مارکیٹ مینجمنٹ اینڈ ڈویلپمنٹ کمپنی", 192f, y, paint)
        y += 42f

        paint.textSize = 21f
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("ڈویژن: ڈیرہ غازی خان  مارکیٹ: مویشی منڈی چوک اعظم", 374f, y, paint)
        y += 42f

        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("ٹھیکیدار:", 374f, y, paint)
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textSize = 19f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(contractor, 10f, y, paint)
        y += 42f

        paint.typeface = nastaleeq
        paint.textSize = 21f
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("تاریخ و وقت:", 374f, y, paint)
        paint.typeface = Typeface.MONOSPACE
        paint.textSize = 18f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(dateTimeStr, 10f, y, paint)
        y += 32f

        fun drawDashedLine(currentY: Float) {
            val dashPaint = Paint().apply {
                color = Color.BLACK
                style = Paint.Style.STROKE
                strokeWidth = 2f
                pathEffect = DashPathEffect(floatArrayOf(6f, 6f), 0f)
            }
            canvas.drawLine(10f, currentY, 374f, currentY, dashPaint)
        }

        drawDashedLine(y)
        y += 38f

        paint.typeface = nastaleeq
        paint.textSize = 24f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("فیس رسید", 192f, y, paint)
        y += 40f

        paint.textSize = 19f
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("فیس کی قسم", 374f, y, paint)
        canvas.drawText("گاڑی نمبر", 275f, y, paint)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("تعداد", 185f, y, paint)
        canvas.drawText("یونٹ", 130f, y, paint)
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("قیمت", 10f, y, paint)
        y += 34f

        paint.typeface = nastaleeq
        paint.textSize = 20f
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(catName, 374f, y, paint)

        paint.typeface = Typeface.MONOSPACE
        paint.textSize = 18f
        canvas.drawText(vehNo, 275f, y, paint)

        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("$qty", 185f, y, paint)
        canvas.drawText("$unitRate", 130f, y, paint)

        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("$basePrice", 10f, y, paint)
        y += 38f

        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textSize = 18f
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("PST(16.0%):", 330f, y, paint)
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("$pst", 10f, y, paint)
        y += 38f

        paint.typeface = nastaleeq
        paint.textSize = 25f
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("کل:", 330f, y, paint)
        paint.typeface = Typeface.MONOSPACE
        paint.textSize = 21f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("$total", 10f, y, paint)
        y += 30f

        drawDashedLine(y)
        y += 38f

        paint.typeface = nastaleeq
        paint.textSize = 22f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("جاری کردہ توسط", 192f, y, paint)
        y += 36f

        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textSize = 21f
        canvas.drawText(operatorName, 192f, y, paint)
        y += 24f

        // QR Pattern
        val qrSize = 135
        val qrLeft = (384 - qrSize) / 2
        val qrBmp = Bitmap.createBitmap(qrSize, qrSize, Bitmap.Config.ARGB_8888)
        val qrCanvas = Canvas(qrBmp)
        qrCanvas.drawColor(Color.WHITE)
        val qrPaint = Paint().apply { color = Color.BLACK }

        qrCanvas.drawRect(0f, 0f, 35f, 35f, qrPaint)
        qrCanvas.drawRect(5f, 5f, 30f, 30f, Paint().apply { color = Color.WHITE })
        qrCanvas.drawRect(10f, 10f, 25f, 25f, qrPaint)

        qrCanvas.drawRect((qrSize - 35).toFloat(), 0f, qrSize.toFloat(), 35f, qrPaint)
        qrCanvas.drawRect((qrSize - 30).toFloat(), 5f, (qrSize - 5).toFloat(), 30f, Paint().apply { color = Color.WHITE })
        qrCanvas.drawRect((qrSize - 25).toFloat(), 10f, (qrSize - 10).toFloat(), 25f, qrPaint)

        qrCanvas.drawRect(0f, (qrSize - 35).toFloat(), 35f, qrSize.toFloat(), qrPaint)
        qrCanvas.drawRect(5f, (qrSize - 30).toFloat(), 30f, (qrSize - 5).toFloat(), Paint().apply { color = Color.WHITE })
        qrCanvas.drawRect(10f, (qrSize - 25).toFloat(), 25f, (qrSize - 10).toFloat(), qrPaint)

        for (i in 40 until qrSize - 40 step 8) {
            for (j in 10 until qrSize - 10 step 8) {
                if ((i + j) % 16 == 0 || (i * j) % 24 == 0) {
                    qrCanvas.drawRect(i.toFloat(), j.toFloat(), (i + 6).toFloat(), (j + 6).toFloat(), qrPaint)
                }
            }
        }
        canvas.drawBitmap(qrBmp, qrLeft.toFloat(), y, null)
        y += qrSize + 26f

        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textSize = 17f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("Transaction QR", 20f, y, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("RAAST ID QR", 364f, y, paint)
        y += 34f

        paint.typeface = Typeface.MONOSPACE
        paint.textSize = 18f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(txnId, 192f, y, paint)
        y += 38f

        paint.typeface = nastaleeq
        paint.textSize = 21f
        canvas.drawText("1233 : ہیلپ لائن", 192f, y, paint)
        y += 38f

        paint.textSize = 17f
        canvas.drawText("30.9923606 / 71.2107413  : GPS مقام", 192f, y, paint)
        y += 40f

        val targetH = y.toInt().coerceAtMost(1320)
        val cropped = Bitmap.createBitmap(bmp, 0, 0, 384, targetH)
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
                        val alpha = Color.alpha(pixel)
                        if (alpha > 40) {
                            val lum = (0.299 * Color.red(pixel) + 0.587 * Color.green(pixel) + 0.114 * Color.blue(pixel)).toInt()
                            if (lum < 145) {
                                slice = slice or (1 shl (7 - b))
                            }
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
                Toast.makeText(this@MainActivity, "16cm پرچی پرنٹ ہو گئی!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "پرنٹ فیل: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
