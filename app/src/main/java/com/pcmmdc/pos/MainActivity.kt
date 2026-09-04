package com.pcmmdc.pos

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

data class AnimalCategory(val nameUrdu: String, val rate: Long)

class MainActivity : ComponentActivity() {

    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

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
                POSScreen()
            }
        }
    }

    @SuppressLint("MissingPermission")
    @Composable
    fun POSScreen() {
        var selectedCat by remember { mutableStateOf(categories[0]) }
        var quantity by remember { mutableIntStateOf(1) }
        var receiptNo by remember { mutableStateOf("260829170356295") }
        var connectedDeviceName by remember { mutableStateOf<String?>(null) }
        var showDeviceDialog by remember { mutableStateOf(false) }

        val basePrice = selectedCat.rate * quantity
        val pstTax = Math.round(basePrice * 0.16)
        val grandTotal = basePrice + pstTax

        Scaffold(
            topBar = {
                Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("PCMMDC POS", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Button(onClick = { showDeviceDialog = true }) {
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
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("جانور کی قسم منتخب کریں:", fontWeight = FontWeight.Bold)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.height(260.dp),
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
                    Text("تعداد (Quantity):", fontSize = 16.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { if (quantity > 1) quantity-- }) { Text("-") }
                        Text("$quantity", modifier = Modifier.padding(horizontal = 16.dp), fontWeight = FontWeight.Bold)
                        Button(onClick = { quantity++ }) { Text("+") }
                    }
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("بنیادی رقم: Rs. $basePrice")
                        Text("PST ٹیکس (16%): Rs. $pstTax")
                        Divider()
                        Text("کل رقم: Rs. $grandTotal", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                }

                OutlinedTextField(
                    value = receiptNo,
                    onValueChange = { receiptNo = it },
                    label = { Text("رسید نمبر") },
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        lifecycleScope.launch {
                            printSlip(selectedCat.nameUrdu, quantity, selectedCat.rate, basePrice, pstTax, grandTotal, receiptNo)
                            receiptNo = (receiptNo.toLongOrNull()?.plus(1) ?: receiptNo).toString()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("پرچی پرنٹ کریں (Print Receipt)", fontSize = 16.sp)
                }
            }
        }

        if (showDeviceDialog) {
            Dialog(onDismissRequest = { showDeviceDialog = false }) {
                Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("دستیاب بلوٹوتھ پرنٹر منتخب کریں", fontWeight = FontWeight.Bold)
                        val bm = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                        val bonded = bm.adapter?.bondedDevices?.toList() ?: emptyList()
                        bonded.forEach { dev ->
                            TextButton(
                                onClick = {
                                    connectToPrinter(dev) { name -> connectedDeviceName = name }
                                    showDeviceDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(dev.name ?: dev.address)
                            }
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToPrinter(device: BluetoothDevice, onConnected: (String) -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                outputStream?.close()
                bluetoothSocket?.close()
                val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
                bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid)
                bluetoothSocket?.connect()
                outputStream = bluetoothSocket?.outputStream
                withContext(Dispatchers.Main) {
                    onConnected(device.name ?: "Connected")
                    Toast.makeText(this@MainActivity, "پرنٹر کنیکٹ ہو گیا", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "کنکشن ناکام: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private suspend fun printSlip(
        catName: String, qty: Int, unitRate: Long, basePrice: Long,
        pst: Long, total: Long, rNo: String
    ) = withContext(Dispatchers.Default) {
        if (outputStream == null) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "پرنٹر منسلک نہیں ہے!", Toast.LENGTH_SHORT).show()
            }
            return@withContext
        }

        val bmp = Bitmap.createBitmap(384, 1050, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 20f
        }

        val nastaleeq = try {
            Typeface.createFromAsset(assets, "fonts/jameel_noori_nastaleeq.ttf")
        } catch (_: Throwable) {
            Typeface.DEFAULT
        }

        var y = 30f

        paint.typeface = nastaleeq
        paint.textSize = 22f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("پنجاب کیٹل مارکیٹ مینجمنٹ اینڈ ڈویلپمنٹ کمپنی", 192f, y, paint)
        y += 40f

        paint.textSize = 18f
        fun drawRow(label: String, value: String) {
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(label, 370f, y, paint)
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText(value, 14f, y, paint)
            y += 30f
        }

        drawRow("ڈویژن", "فیصل آباد")
        drawRow("مارکیٹ", "ماڈل مویشی منڈی جھنگ سٹی")
        drawRow("نام ٹھیکیدار", "محمد اسماعیل")
        drawRow("نام آپریٹر", "M Yasir Hameed")

        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("رسید نمبر", 192f, y, paint)
        y += 25f
        paint.typeface = Typeface.MONOSPACE
        canvas.drawText(rNo, 192f, y, paint)
        y += 30f

        paint.typeface = nastaleeq
        canvas.drawText("تاریخ و وقت", 192f, y, paint)
        y += 25f
        paint.typeface = Typeface.MONOSPACE
        val timeStr = SimpleDateFormat("dd/MM/yyyy hh:mm:ss a", Locale.US).format(Date())
        canvas.drawText(timeStr, 192f, y, paint)
        y += 25f

        paint.strokeWidth = 2f
        canvas.drawLine(14f, y, 370f, y, paint)
        y += 30f

        paint.typeface = nastaleeq
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("فیس رسید", 192f, y, paint)
        y += 30f

        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("فیس کی قسم", 370f, y, paint)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("تعداد", 240f, y, paint)
        canvas.drawText("یونٹ", 150f, y, paint)
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("قیمت", 14f, y, paint)
        y += 20f

        canvas.drawLine(14f, y, 370f, y, paint)
        y += 25f

        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(catName, 370f, y, paint)
        paint.typeface = Typeface.MONOSPACE
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("$qty", 240f, y, paint)
        canvas.drawText("$unitRate", 150f, y, paint)
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("$basePrice", 14f, y, paint)
        y += 25f

        canvas.drawLine(14f, y, 370f, y, paint)
        y += 30f

        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("PST(16%)", 370f, y, paint)
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("$pst", 14f, y, paint)
        y += 30f

        paint.typeface = nastaleeq
        paint.textSize = 22f
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("کل", 370f, y, paint)
        paint.typeface = Typeface.MONOSPACE
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("$total", 14f, y, paint)
        y += 35f

        paint.typeface = nastaleeq
        paint.textSize = 24f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("اداشدہ", 192f, y, paint)
        y += 40f

        paint.textSize = 16f
        canvas.drawText("1233 : ہیلپ لائن", 192f, y, paint)
        y += 25f
        canvas.drawText("+92 323 1233000 : واٹس ایپ", 192f, y, paint)
        y += 25f
        canvas.drawText("31.215677, 72.355752 : GPS مقام", 192f, y, paint)
        y += 30f
        paint.typeface = Typeface.MONOSPACE
        canvas.drawText("Powered by PCMMDC", 192f, y, paint)
        y += 50f

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
                        if (lum < 130) {
                            slice = slice or (1 shl (7 - b))
                        }
                    }
                }
                stream.write(slice)
            }
        }
        stream.write(byteArrayOf(0x1B, 0x64, 0x03))

        try {
            outputStream?.write(stream.toByteArray())
            outputStream?.flush()
        } catch (_: Exception) {}
    }
}
