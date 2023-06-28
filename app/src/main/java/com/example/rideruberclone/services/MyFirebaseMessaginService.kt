package com.example.rideruberclone.services

import com.example.rideruberclone.Constants
import com.example.rideruberclone.utils.UserUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        if (FirebaseAuth.getInstance().currentUser != null) {
            UserUtils.updateToken(this, token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Constants.showNotification(
            this, kotlin.random.Random.nextInt(),
            message.notification?.title,
            message.notification?.body,
            null
        )
    }
}