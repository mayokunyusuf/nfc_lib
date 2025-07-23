Android MIFARE Classic NFC Service Library

A reusable Android library module (.aar) for seamless interaction with MIFARE Classic 1K/4K NFC tags. This project simplifies NFC detection, reading, and writing through a clean, service-based architecture, making it ideal for POS applications or any app requiring NFC tag interaction.

✨ Features
Simple Integration: Add NFC capabilities to your project by just including the .aar module.

Reliable Scanning: Uses a foreground NfcService to ensure stable tag reading while your app is active.

Modern API Usage: Built with Android’s recommended NfcAdapter.ReaderCallback for efficient NFC handling.

POS Ready: Designed with Point-of-Sale system workflows in mind.

Lifecycle Aware: Easily manage the service lifecycle from any Activity or Fragment.

🛠️ Setup Guide
Follow these steps to integrate the NFC service into your Android application.

Step 1: Add the Library (.aar) to Your Project
Copy your .aar file (e.g., nfc-service.aar) into your project's libs directory. If the directory doesn't exist, create it at the root of your project.

Include the library in your settings.gradle.kts (or settings.gradle) file:

dependencyResolutionManagement {
    repositories {
        //...
        flatDir {
            dirs("libs")
        }
    }
}

Add the library as a dependency in your app-level build.gradle.kts (or build.gradle):

dependencies {
    //...
    implementation(name = "nfc-service", ext = "aar")
}

Step 2: Update AndroidManifest.xml
Declare NFC permission and set up an intent filter to allow your app to handle NFC events. Add the following inside your <application> tag:

<!-- Required NFC Permission -->
<uses-permission android:name="android.permission.NFC" />

<application ...>
    <activity
        android:name=".YourActivity"
        android:launchMode="singleTop">

        <!-- Intent filter to handle discovered NFC tags -->
        <intent-filter>
            <action android:name="android.nfc.action.TECH_DISCOVERED" />
        </intent-filter>

        <meta-data
            android:name="android.nfc.action.TECH_DISCOVERED"
            android:resource="@xml/nfc_tech_filter" />

    </activity>
</application>

Step 3: Create NFC Tech Filter
To ensure your app only responds to MIFARE Classic tags, create a new XML file at res/xml/nfc_tech_filter.xml:

<?xml version="1.0" encoding="utf-8"?>
<resources>
    <tech-list>
        <tech>android.nfc.tech.MifareClassic</tech>
    </tech-list>
</resources>

🚀 How to Use
The service is designed to be started on-demand (e.g., when a user presses a "Scan Card" button).

Start the Service: From your Activity or Fragment, create an intent and start the NfcService.

// In your Fragment or Activity
private fun startNfcScan() {
    val nfcServiceIntent = Intent(this, NfcService::class.java).apply {
        // Optionally add extra data to the intent
        putExtra("SCAN_MODE", "READ_USER_DATA")
    }
    startService(nfcServiceIntent)
    // The service will now run in the foreground and listen for NFC tags
}

Receive Data: The service can be configured to return data via a BroadcastReceiver, LiveData, or another callback mechanism. The example below uses a BroadcastReceiver.

private val nfcResultReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == NfcService.ACTION_NFC_RESULT) {
            val tagPayload = intent.getStringExtra(NfcService.EXTRA_NFC_PAYLOAD)
            Log.d("NFC", "Received tag data: $tagPayload")
            // Process the payload
        }
    }
}

override fun onResume() {
    super.onResume()
    registerReceiver(nfcResultReceiver, IntentFilter(NfcService.ACTION_NFC_RESULT))
}

override fun onPause() {
    super.onPause()
    unregisterReceiver(nfcResultReceiver)
}

📋 Requirements
Android 6.0+ (API 23)

NFC-enabled physical device
