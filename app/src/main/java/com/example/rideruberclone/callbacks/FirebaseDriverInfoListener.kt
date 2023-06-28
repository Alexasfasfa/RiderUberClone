package com.example.rideruberclone.callbacks

import com.example.rideruberclone.models.DriverGeoModel


interface FirebaseDriverInfoListener {
    fun onDriverInfoLoadSuccess(driverGeoModel: DriverGeoModel?)
}