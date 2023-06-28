package com.example.rideruberclone

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.rideruberclone.models.DriverGeoModel
import com.example.rideruberclone.models.RiderModel
import com.google.android.gms.maps.model.Marker

object Constants {
    val markerList: HashMap<String, Marker> = HashMap()
    val DRIVER_INFO_REFERENCE: String = "DriverInfo"
    val driversFound: HashSet<DriverGeoModel> = HashSet()
    val DRIVERS_LOCATION_REFERENCE: String = "DriversLocation"
    val TOKEN_REFERENCE: String = "Token"
    var currentRider: RiderModel? = null
    val RIDER_INFO_REFERENCE = "Riders"

    fun buildWelcomeMessage(): String {
        return StringBuilder("Welcome, ")
            .append(currentRider?.firstName)
            .append(" ")
            .append(currentRider?.lastName)
            .toString()
    }

    fun showNotification(
        context: Context,
        id: Int,
        title: String?,
        body: String?,
        intent: Intent?
    ) {
        var pendingIntent: PendingIntent? = null
        if (intent != null)
            pendingIntent =
                PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        val NOTIFICATION_CHANNEL_ID = "com.example.rideruberclone"
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID, "Uber Clone",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationChannel.description = "Uber Clone"
            notificationChannel.enableLights(true)
            notificationChannel.lightColor = Color.RED
            notificationChannel.vibrationPattern = longArrayOf(0, 1000, 500, 1000)
            notificationChannel.enableVibration(true)

            notificationManager.createNotificationChannel(notificationChannel)
        }


        val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
        builder.setContentTitle(title)

        builder.setAutoCancel(false)
        builder.setContentText(body)
        builder.setPriority(NotificationCompat.PRIORITY_HIGH)
        builder.setDefaults(android.app.Notification.DEFAULT_VIBRATE)
        builder.setSmallIcon(R.drawable.ic_car)
        builder.setLargeIcon(
            BitmapFactory.decodeResource(
                context.resources,
                R.drawable.ic_car
            )
        )

        if (pendingIntent != null)
            builder.setContentIntent(pendingIntent)
        val notification = builder.build()
        notificationManager.notify(id, notification)
        Log.d("TITLE",title!!)
    }

    fun buildName(firstName: String, lastName: String): String {
        return StringBuilder(firstName).append(" ").append(lastName).toString()
    }
}