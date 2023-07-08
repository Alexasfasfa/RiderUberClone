package com.example.rideruberclone.Remote

import io.reactivex.rxjava3.core.Observable
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

interface FCMService {
    @Headers(
        "Content-Type:application/json",
        "Authorization:key=AAAAdExuFu4:APA91bEPNgZD_U-HRpu4YZZw1LmRYNDa--NlKw_k0ZBhKlcdI_VrsyTfls_gZnZxnmg_TH7M2ssVZg_sahueeA-sA4lO0kZuaU3WksidnxXKD5Tj-j_Ru2rvmeFhLy_qydKoYJ4PcqcD"
    )

    @POST("fcm/send")
    fun sendNotification(@Body body: FCMSendData?): Observable<FCMResponse>
}