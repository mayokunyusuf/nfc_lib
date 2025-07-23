package com.techies.t1getpayed.nfc

import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.MifareClassic
import android.util.Log
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import java.io.IOException
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Date

class NfcService(private val activity: Activity,
                 private var terminalId: String?,
                 private var merchantId: String?,
                 private var terminalName: String?) {

    private val techList = arrayOf(arrayOf(IsoDep::class.java.name))

    private var nfcAdapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity).also {
        if (it == null) Toast.makeText(activity, "NFC is not supported on this device.", Toast.LENGTH_LONG).show()
    }
    private val ndefIntentFilter = IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED).apply {
        try {
            addDataType("*/*")
        } catch (e: Exception) {
            throw RuntimeException("Failed to setup NFC intent filter.", e)
        }
    }

    fun enableForegroundDispatch() {
        val intent = Intent(activity, activity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(activity, 0, intent, PendingIntent.FLAG_MUTABLE)
        nfcAdapter?.enableForegroundDispatch(activity, pendingIntent, arrayOf(ndefIntentFilter), techList)
    }

    fun disableForegroundDispatch() {
        nfcAdapter?.disableForegroundDispatch(activity)
    }

    fun handleIntent(intent: Intent) {
        when (intent.action) {
            NfcAdapter.ACTION_NDEF_DISCOVERED,
            NfcAdapter.ACTION_TAG_DISCOVERED,
            NfcAdapter.ACTION_TECH_DISCOVERED -> {
                handleTechDiscovered(intent)
            }
            else -> Toast.makeText(activity, "Unhandled NFC action: ${intent.action}", Toast.LENGTH_LONG).show()
        }
    }

    private fun handleTechDiscovered(intent: Intent) {
        val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
        tag?.let { detectedTag ->

            if (MifareClassic.get(detectedTag) != null) {
                readFromTag(detectedTag)
            }
        }
    }

    private fun promptUserForAction(tag: Tag) {
        AlertDialog.Builder(activity).apply {
            setTitle("Choose an Action")
            setItems(arrayOf("Read from Tag", "Write to Tag")) { dialog, which ->
                when (which) {
                    0 -> readFromTag(tag)
                    1 -> promptForInputAndWrite(tag)
                }
            }
            show()
        }
    }

    fun readFromTag(tag: Tag) {
        val mifare = MifareClassic.get(tag)
        if (mifare != null) {
            try {
                mifare.connect()
                val readableData = StringBuilder()

                // Loop through all sectors and blocks
                for (sectorIndex in 0 until mifare.sectorCount) {
                    if (mifare.authenticateSectorWithKeyB(sectorIndex, MifareClassic.KEY_DEFAULT)) {
                        val blockIndex = mifare.sectorToBlock(sectorIndex)
                        val blocksCount = mifare.getBlockCountInSector(sectorIndex)

                        for (j in 0 until blocksCount) {
                            val blockNumber = blockIndex + j
                            val blockData = mifare.readBlock(blockNumber)
                            val text = String(blockData)
                            // Use regex to find and append only readable parts
                            val matcher = Regex("[\\x20-\\x7E&&[^@]]+").findAll(text)
                            for (match in matcher) {
                                readableData.append(match.value).append("")
                            }
                        }
                    }
                }

                // Display the accumulated data if any
                if (readableData.isNotEmpty()) {
                    AlertDialog.Builder(activity).apply {
                        setMessage("Data Read from Tag: $readableData")
                        show()
                    }
                } else {
                    Toast.makeText(activity, "No readable text found on the tag.", Toast.LENGTH_LONG).show()
                }

            } catch (e: IOException) {
                Toast.makeText(activity, "IOException while reading tag: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                if (mifare.isConnected) {
                    mifare.close()
                }
            }
        } else {
            Toast.makeText(activity, "This is not a MIFARE Classic Tag.", Toast.LENGTH_LONG).show()
        }
    }

    private fun attemptMultipleEncodings(block: ByteArray): String {
        val encodings = arrayOf("UTF-8", "ISO-8859-1", "ASCII")
        for (encoding in encodings) {
            try {
                val text = String(block, Charset.forName(encoding)).trim { it <= ' ' }
                if (text.isNotEmpty()) return text
            } catch (e: Exception) {
                Log.e("Read Error", "Failed with $encoding: ${e.localizedMessage}")
            }
        }
        return ""
    }

    private fun promptForInputAndWrite(tag: Tag) {
        val input = EditText(activity).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
        }
        AlertDialog.Builder(activity).apply {
            setTitle("Enter Text to Write")
            setView(input)
            setPositiveButton("Write") { dialog, _ ->
                writeToTag(tag, input.text.toString())
            }
            setNegativeButton("Cancel", null)
            create()
            show()
        }
    }

    private fun writeToTag(tag: Tag, message: String) {
        val mifare = MifareClassic.get(tag)
        if (mifare != null) {
            try {
                mifare.connect()
                val messageBytes = message.toByteArray(Charset.forName("UTF-8"))
                val defaultKeyB = MifareClassic.KEY_DEFAULT

                // Authenticate with the sector where you intend to write
                val sectorIndex = 1  // Adjust as necessary
                if (mifare.authenticateSectorWithKeyB(sectorIndex, defaultKeyB)) {
                    val blockIndex = mifare.sectorToBlock(sectorIndex)
                    val blocksPerSector = mifare.getBlockCountInSector(sectorIndex)
                    val numBlocks = Math.ceil(messageBytes.size / 16.0).toInt()

                    for (i in 0 until numBlocks) {
                        if (i >= blocksPerSector) {
                            Toast.makeText(activity, "Message too long for single sector; try a shorter message or additional sectors.", Toast.LENGTH_LONG).show()
                            break
                        }
                        val start = i * 16
                        val end = Math.min(start + 16, messageBytes.size)
                        val blockBytes = messageBytes.copyOfRange(start, end).copyOf(16) // Ensure block is exactly 16 bytes

                        mifare.writeBlock(blockIndex + i, blockBytes)
                    }
//                    printReceipt(message)
                    Toast.makeText(activity, "Message written to tag successfully.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(activity, "Authentication with key B failed.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(activity, "Error writing to MIFARE Classic tag: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                Log.e("MIFARE Write Error", "Error: ${e.localizedMessage}")
            } finally {
                if (mifare.isConnected) {
                    mifare.close()
                }
            }
        } else {
            Toast.makeText(activity, "This is not a MIFARE Classic Tag.", Toast.LENGTH_LONG).show()
        }
    }
}
