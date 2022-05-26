package dev.ujjwal.transactionalsmsanalyzer.view.activity

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.recyclerview.widget.LinearLayoutManager
import dev.ujjwal.transactionalsmsanalyzer.R
import dev.ujjwal.transactionalsmsanalyzer.model.SMSDetail
import dev.ujjwal.transactionalsmsanalyzer.model.smsList
import dev.ujjwal.transactionalsmsanalyzer.util.getAmount
import dev.ujjwal.transactionalsmsanalyzer.util.getCreditStatus
import dev.ujjwal.transactionalsmsanalyzer.util.gotoChatActivity
import dev.ujjwal.transactionalsmsanalyzer.view.adapter.SmsListAdapter
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*
import java.util.regex.Pattern


class MainActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences

    companion object {
        private val TAG = MainActivity::class.java.simpleName
        private const val MY_PERMISSIONS_REQUEST_READ_SMS = 1
    }

    private val smsListAdapter = SmsListAdapter(this, arrayListOf())

    val balanceKeywords = arrayOf(
        "avbl bal",
        "available balance",
        "a/c bal",
        "available bal",
        "avl bal",
        "new balance",
        "new bal",
        "current balance",
        "current bal"

    )

    fun getBalance(msg: String):String {
        if (msg.isNullOrEmpty()) {
            return ""
        }

        val message = processMessage(msg).plus(' ');
        var indexOfKeyword = -1;
        var balance = "";

        balanceKeywords.takeWhile { word ->
            indexOfKeyword = message.toLowerCase().indexOf(word);
            if (indexOfKeyword > -1) {
                indexOfKeyword += word.length;
                return@takeWhile false;
            }
            return@takeWhile true;
        }

        if(indexOfKeyword<0){
            return ""
        }
        // found the index of keyword, moving on to finding 'rs.' occuring after index_of_keyword
        var index = indexOfKeyword;
        var indexOfRs = -1;
        var nextThreeChars = message.toLowerCase().substring(index, index.plus(3));

        index += 3;

        while (index < message.length) {
            // discard first char
            nextThreeChars = nextThreeChars.substring(1);
            // add the current char at the end
            nextThreeChars += message[index];

            if (nextThreeChars.equals("rs.", true)) {
                indexOfRs = index + 1;
                break;
            }

            ++index;
        }

        // no occurence of 'rs.'
        if (indexOfRs == -1) {
            return "";
        }

        balance = extractBalance(indexOfRs, message, message.length);

        return balance;
    }



    fun processMessage(msg: String): String {
        // convert to lower case
        var message = msg.toLowerCase()
        // remove '-'
        message = message.replace("-", "");
        // remove ':'
        message = message.replace(":", " ");
        // remove '/'
        message = message.replace("/", "");
        // remove 'ending'
        message = message.replace("ending", "");
        // replace 'x'
        message = message.replace("x", "");
        // replace 'is'
        message = message.replace("is", "");
        // replace 'with'
        message = message.replace("with", "");
        // remove 'no.'
        message = message.replace("no. ", "");
        // replace acct with ac
        message = message.replace("acc", "ac");
        // replace account with ac
        message = message.replace("account", "ac");
        // replace all 'rs ' with 'rs. '
        message = message.replace("rs ", "rs. ");
        // replace all inr with rs.
        message = message.replace("inr", "rs. ");
        //
        message = message.replace("inr ", "rs. ");
        // replace all 'rs. ' with 'rs.'
        message = message.replace("rs. ", "rs.");
        // replace all 'rs.' with 'rs. '
        message = message.replace("rs.", "rs. ");
        // split message into words
        message.split(' ');
        // remove '' from array
        return message;
    }

    private fun extractBalance(index: Int, message: String, length: Int): String {
        var balance = "";
        var digitFound = false;
        var invalidCharCount = 0;
        var char = ' ';
        var indexOfAmount = index;
        while (indexOfAmount < length) {
            char = message[indexOfAmount]

            if (char.isDigit()) {
                digitFound = true;
                // is_start = false;
                balance += char;
            } else {
                if (!digitFound) {
                } else {
                    if (char == '.') {
                        if (invalidCharCount == 1) {
                            break;
                        } else {
                            balance += char;
                            invalidCharCount += 1;
                        }
                    } else if (char != ',') {
                        break;
                    }
                }
            }
            indexOfAmount += 1;
        };

        return balance;
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPreferences = this.getSharedPreferences("TagPref", Context.MODE_PRIVATE)

        checkPermission()

        recycler_view.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = smsListAdapter
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
            refreshSmsInbox()
        }

        btn_total.setOnClickListener {
            gotoChatActivity("total")
        }
        btn_monthly.setOnClickListener {
            gotoChatActivity("monthly")
        }
        btn_daily.setOnClickListener {
            gotoChatActivity("daily")
        }
        btn_tag.setOnClickListener {
            gotoChatActivity("tag")
        }

        et_search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                filter(s.toString().trim())
            }
        })
        showNotification()
    }

    private fun checkPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_SMS), MY_PERMISSIONS_REQUEST_READ_SMS)
        }
    }

    private fun showNotification(){
        // Create an explicit intent for an Activity in your app
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val notificationId = (Date().time / 1000L % Int.MAX_VALUE).toInt()
        val notification = NotificationCompat.Builder(applicationContext, "notify_001")
            .setAutoCancel(false)
//            .setOngoing(true)
            .setContentTitle("SMS")
            .setContentText("This is the content text.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("This is the content Big text."))
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setDefaults(Notification.DEFAULT_VIBRATE)
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.mipmap.ic_launcher)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "Your_channel_id"
            val channel = NotificationChannel(
                channelId,
                "Channel human readable title",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
            notification.setChannelId(channelId)
        }


        notificationManager.notify(notificationId, notification.build())
//        with(NotificationManagerCompat.from(this)) {
//            // notificationId is a unique int for each notification that you must define
//            notify(notificationId, notification)
//        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            MY_PERMISSIONS_REQUEST_READ_SMS -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    Log.i(TAG, "Permission was granted for Read SMS")
                    refreshSmsInbox()
                } else {
                    Log.i(TAG, "Permission was denied for Read SMS")
                    Toast.makeText(applicationContext, "Permission was denied for Read SMS", Toast.LENGTH_LONG).show()
                }
            }

            else -> {
                // Ignore all other requests.
            }
        }
    }

    private fun refreshSmsInbox() {
        val contentResolver = contentResolver
        val smsInboxCursor: Cursor? = contentResolver.query(Uri.parse("content://sms/inbox"), null, null, null, null)
        val indexId: Int = smsInboxCursor!!.getColumnIndex("_id")
        val indexDate: Int = smsInboxCursor.getColumnIndex("date")
        val indexBody: Int = smsInboxCursor.getColumnIndex("body")
        val indexAddress: Int = smsInboxCursor.getColumnIndex("address")
        if (indexBody < 0 || !smsInboxCursor.moveToFirst()) return
        smsList.clear()
        do {
            val id = smsInboxCursor.getString(indexId).trimIndent()
            val date = smsInboxCursor.getString(indexDate).trimIndent()
            val sender = smsInboxCursor.getString(indexAddress).trimIndent()
            val body = smsInboxCursor.getString(indexBody).trimIndent()

            val isValidTransaction = body.contains("credited") || body.contains("received") || body.contains("debited") || body.contains("withdrawn")
            val regEx = Pattern.compile("(?i)(?:(?:RS|INR|MRP)\\.?\\s?)(\\d+(:?\\,\\d+)?(\\,\\d+)?(\\.\\d{1,2})?)")
            val m = regEx.matcher(body)
            if (isValidTransaction && m.find()) {
                val smsDetail = SMSDetail()
                smsDetail._id = id
                smsDetail.date = date
                smsDetail.sender = sender
                smsDetail.body = body
                smsDetail.isCredited = getCreditStatus(body)
                smsDetail.amount = getAmount(m.group(0)!!)
                smsDetail.tag = sharedPreferences.getString(id, "")
                smsDetail.balance = getBalance(body)
                smsList.add(smsDetail)
            }
        } while (smsInboxCursor.moveToNext())
        smsListAdapter.updateSms(smsList)
    }

    private fun filter(text: String) {
        val filterSmsList = ArrayList<SMSDetail>()
        for (sms in smsList) {
            if (sms.tag!!.toLowerCase().contains(text.toLowerCase())) {
                filterSmsList.add(sms)
            }
            smsListAdapter.updateSms(filterSmsList)
        }
    }

    fun changeSmsTag(id: String) {
        val alertDialog: AlertDialog.Builder = AlertDialog.Builder(this)
        alertDialog.setMessage("Change Tag Name")
        val input = EditText(this)
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        )
        input.layoutParams = lp
        alertDialog.setView(input)
        input.setText(sharedPreferences.getString(id, ""))

        alertDialog.setPositiveButton("Save") { dialog, which ->
            val str = input.text.toString().trim().toLowerCase()
            val editor: SharedPreferences.Editor = sharedPreferences.edit()
            editor.putString(id, str)
            editor.apply()
            refreshSmsInbox()
        }
        alertDialog.setNegativeButton("Cancel") { dialog, which -> dialog.dismiss() }

        alertDialog.show()
    }
}