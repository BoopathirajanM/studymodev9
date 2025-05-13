package com.example.studymodev9

import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Transparent activity that handles contact picking and initiates phone calls
 */
class ContactPickerActivity : ComponentActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private var pendingContactUri: Uri? = null

    private lateinit var contactPickerLauncher: ActivityResultLauncher<Void?>
    private lateinit var callPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var contactsPermissionLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        contactPickerLauncher = registerForActivityResult(
            ActivityResultContracts.PickContact()
        ) { uri ->
            uri?.let { processContactSelection(it) }
        }

        callPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                pendingContactUri?.let { processContactSelection(it) }
            } else {
                Toast.makeText(this, "Call permission is required to make emergency calls", Toast.LENGTH_LONG).show()
                finishAndReturn(false)
            }
        }

        contactsPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                checkCallPermissionAndLaunchPicker()
            } else {
                Toast.makeText(this, "Contacts permission is required to access emergency contacts", Toast.LENGTH_LONG).show()
                finishAndReturn(false)
            }
        }

        checkPermissionsAndLaunchPicker()
    }

    private fun checkPermissionsAndLaunchPicker() {
        when {
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_CONTACTS) 
                == PackageManager.PERMISSION_GRANTED -> {
                checkCallPermissionAndLaunchPicker()
            }
            shouldShowRequestPermissionRationale(android.Manifest.permission.READ_CONTACTS) -> {
                showContactsPermissionRationaleDialog()
            }
            else -> {
                contactsPermissionLauncher.launch(android.Manifest.permission.READ_CONTACTS)
            }
        }
    }

    private fun checkCallPermissionAndLaunchPicker() {
        when {
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.CALL_PHONE) 
                == PackageManager.PERMISSION_GRANTED -> {
                launchContactPicker()
            }
            shouldShowRequestPermissionRationale(android.Manifest.permission.CALL_PHONE) -> {
                showCallPermissionRationaleDialog()
            }
            else -> {
                callPermissionLauncher.launch(android.Manifest.permission.CALL_PHONE)
            }
        }
    }

    private fun showContactsPermissionRationaleDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Contacts Permission Required")
            .setMessage("Contacts permission is needed to access your emergency contacts.")
            .setPositiveButton("Grant") { _, _ ->
                contactsPermissionLauncher.launch(android.Manifest.permission.READ_CONTACTS)
            }
            .setNegativeButton("Cancel") { _, _ ->
                finishAndReturn(false)
            }
            .setCancelable(false)
            .show()
    }

    private fun showCallPermissionRationaleDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Call Permission Required")
            .setMessage("Call permission is needed to make emergency calls in case of need.")
            .setPositiveButton("Grant") { _, _ ->
                callPermissionLauncher.launch(android.Manifest.permission.CALL_PHONE)
            }
            .setNegativeButton("Cancel") { _, _ ->
                finishAndReturn(false)
            }
            .setCancelable(false)
            .show()
    }

    private fun launchContactPicker() {
        contactPickerLauncher.launch(null)
    }

    private fun processContactSelection(contactUri: Uri) {
        try {
            // First, get the contact ID from the URI
            val contactId = contactUri.lastPathSegment
            
            // Query the contact's phone numbers
            val phoneUri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.TYPE,
                ContactsContract.CommonDataKinds.Phone.LABEL
            )
            
            val selection = "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?"
            val selectionArgs = arrayOf(contactId)
            
            contentResolver.query(phoneUri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    
                    if (numberIndex >= 0) {
                        val phoneNumber = cursor.getString(numberIndex)
                        
                        if (phoneNumber.isNotBlank()) {
                            // Get the contact name
                            val nameProjection = arrayOf(ContactsContract.Contacts.DISPLAY_NAME)
                            contentResolver.query(contactUri, nameProjection, null, null, null)?.use { nameCursor ->
                                if (nameCursor.moveToFirst()) {
                                    val nameIndex = nameCursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                                    val contactName = if (nameIndex >= 0) {
                                        nameCursor.getString(nameIndex) ?: "Unknown Contact"
                                    } else {
                                        "Unknown Contact"
                                    }
                                    
                                    makeCall(phoneNumber, contactName)
                                    return
                                }
                            }
                        }
                    }
                }
            }
            
            Toast.makeText(this, "Could not get phone number", Toast.LENGTH_SHORT).show()
            finishAndReturn(false)
            
        } catch (e: Exception) {
            Log.e("ContactPicker", "Error processing contact selection", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            finishAndReturn(false)
        }
    }

    private fun makeCall(phoneNumber: String, contactName: String) {
        try {
            val callIntent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_NO_HISTORY
            }
            
            startActivity(callIntent)
            
            Log.d("ContactPicker", "Starting call to $contactName ($phoneNumber)")
            Toast.makeText(this, "Calling $contactName...", Toast.LENGTH_SHORT).show()
            
            val broadcastIntent = Intent("com.example.studymodev9.CALL_STARTED").apply {
                putExtra("PHONE_NUMBER", phoneNumber)
                putExtra("CONTACT_NAME", contactName)
            }
            sendBroadcast(broadcastIntent)
            
            handler.postDelayed({
                finishAndReturn(true)
            }, 500)
            
        } catch (e: Exception) {
            Log.e("ContactPicker", "Error making call", e)
            Toast.makeText(this, "Error making call: ${e.message}", Toast.LENGTH_SHORT).show()
            finishAndReturn(false)
        }
    }

    private fun finishAndReturn(success: Boolean) {
        val resultIntent = Intent().apply {
            putExtra("CALL_INITIATED", success)
        }
        setResult(if (success) RESULT_OK else RESULT_CANCELED, resultIntent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
} 