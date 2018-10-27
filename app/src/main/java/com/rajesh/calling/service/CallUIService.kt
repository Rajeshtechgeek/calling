package com.rajesh.calling.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.support.v4.app.NotificationCompat
import android.util.Log
import android.widget.Toast
import com.rajesh.calling.BuildConfig
import com.rajesh.calling.CallUIActivity
import com.rajesh.calling.MainActivity
import com.rajesh.calling.R


class CallUIService : Service() {

    private val mBinder = LocalBinder()

    /**
     * Used to check whether the bound activity has really gone away and not unbound as part of an
     * orientation change. We create a foreground service notification only if the former takes
     * place.
     */
    private var mChangingConfiguration = false

    private var timeRemainingText = "00:00"

    private val delayToShowCallUI: Long = 1 * 60 * 1000;//1 Minute

    private var remainingTime: Long = delayToShowCallUI

    private var mNotificationManager: NotificationManager? = null

    private val handler: Handler = Handler()

    private val callUIRunnable = Runnable {
        startActivity(Intent(this, CallUIActivity::class.java))
    }

    private val timerRunnable = object: Runnable {
        override fun run() {
            if (remainingTime >= 1000) {
                remainingTime -= 1000

                val second = remainingTime / 1000 % 60
                val minute = remainingTime / (1000 * 60) % 60

                timeRemainingText = String.format("%02d:%02d", minute, second)

                handler.postDelayed(this, 1000)

                onTimeUpdated()
            }
        }
    }

    /**
     * Returns the [NotificationCompat] used as part of the foreground service.
     */
    private val notification: Notification
        get() {
            val intent = Intent(this, CallUIService::class.java)
            // Extra to help us figure out if we arrived in onStartCommand via the notification or not.
            intent.putExtra(EXTRA_STARTED_FROM_NOTIFICATION, true)
            // The PendingIntent that leads to a call to onStartCommand() in this service.
            val servicePendingIntent = PendingIntent.getService(this, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT)
            // The PendingIntent to launch activity.
            val activityPendingIntent = PendingIntent.getActivity(this, 0,
                    Intent(this, MainActivity::class.java), 0)

            val builder = NotificationCompat.Builder(this, CHANNEL_ID)
                    .addAction(R.drawable.ic_launch, getString(R.string.launch_activity),
                            activityPendingIntent)
                    .addAction(R.drawable.ic_cancel, getString(R.string.stop),
                            servicePendingIntent)
                    .setContentText("Remaining: " + timeRemainingText)
                    .setContentTitle("Call UI")
                    .setOngoing(true)
                    .setSmallIcon(R.drawable.ic_cancel)
                    .setTicker("Remaining Time :" + timeRemainingText)
                    .setWhen(System.currentTimeMillis())

            return builder.build()
        }

    override fun onCreate() {

        mNotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Android O requires a Notification Channel.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.app_name)
            // Create the channel for the notification
            val mChannel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_DEFAULT)
            mChannel.setSound(null, null)

            // Set the Notification Channel for the Notification Manager.
            mNotificationManager!!.createNotificationChannel(mChannel)
        }

        Toast.makeText(this, "service created", Toast.LENGTH_SHORT).show()

        /* posting call ui Task to handler */
        handler.postDelayed(callUIRunnable, delayToShowCallUI)
        handler.postDelayed(timerRunnable, 1000)
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.i(TAG, "Service started")
        val startedFromNotification = intent.getBooleanExtra(EXTRA_STARTED_FROM_NOTIFICATION,
                false)

        // We got here because the user decided to remove battery updates from the notification.
        if (startedFromNotification) {
            stopSelf()
        }
        // Tells the system to not try to recreate the service after it has been killed.
        return Service.START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        mChangingConfiguration = true
    }

    override fun onBind(intent: Intent): IBinder? {
        // Called when a client (MainActivity in case of this sample) comes to the foreground
        // and binds with this service. The service should cease to be a foreground service
        // when that happens.
        Log.i(TAG, "in onBind()")
        stopForeground(true)
        mChangingConfiguration = false
        return mBinder
    }

    override fun onRebind(intent: Intent) {
        // Called when a client (MainActivity in case of this sample) returns to the foreground
        // and binds once again with this service. The service should cease to be a foreground
        // service when that happens.
        Log.i(TAG, "in onRebind()")
        stopForeground(true)
        mChangingConfiguration = false
        super.onRebind(intent)
    }

    override fun onUnbind(intent: Intent): Boolean {
        Log.i(TAG, "Last client unbound from service")

        // Called when the last client (MainActivity in case of this sample) unbinds from this
        // service. If this method is called due to a configuration change in MainActivity, we
        // do nothing. Otherwise, we make this service a foreground service.
        if (!mChangingConfiguration) {
            Log.i(TAG, "Starting foreground service")

            startForeground(NOTIFICATION_ID, notification)
        }
        return true // Ensures onRebind() is called when a client re-binds.
    }

    override fun onDestroy() {
        Toast.makeText(this, "service destroyed", Toast.LENGTH_SHORT).show()
        handler.removeCallbacks(callUIRunnable)
        handler.removeCallbacks(timerRunnable)
    }


    private fun onTimeUpdated() {

        // Update notification content if running as a foreground service.
        if (serviceIsRunningInForeground(this)) {
            mNotificationManager!!.notify(NOTIFICATION_ID, notification)
        }

    }


    /**
     * Class used for the client Binder.  Since this service runs in the same process as its
     * clients, we don't need to deal with IPC.
     */
    inner class LocalBinder : Binder() {
        internal val service: CallUIService
            get() = this@CallUIService
    }

    /**
     * Returns true if this is a foreground service.
     *
     * @param context The [Context].
     */
    fun serviceIsRunningInForeground(context: Context): Boolean {
        val manager = context.getSystemService(
                Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(
                Integer.MAX_VALUE)) {
            if (javaClass.name == service.service.className) {
                if (service.foreground) {
                    return true
                }
            }
        }
        return false
    }

    companion object {

        private val PACKAGE_NAME = BuildConfig.APPLICATION_ID

        private val TAG = CallUIService::class.java.simpleName

        /**
         * The name of the channel for notifications.
         */
        private val CHANNEL_ID = "optisol_task"

        private val EXTRA_STARTED_FROM_NOTIFICATION = "$PACKAGE_NAME.started_from_notification"

        /**
         * The identifier for the notification displayed for the foreground service.
         */
        private val NOTIFICATION_ID = 18888
    }
}