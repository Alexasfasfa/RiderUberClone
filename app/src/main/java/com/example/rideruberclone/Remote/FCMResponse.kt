package com.example.rideruberclone.Remote

import com.example.rideruberclone.models.FCMResult

class FCMResponse {
    var multicast_id: Long = 0
    var success = 0
    var failure = 0
    var canonical_ids = 0
    var results: List<FCMResult>? = null
    var message_id: Long = 0
}