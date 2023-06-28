package com.example.rideruberclone

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.example.rideruberclone.activities.HomeActivity
import com.example.rideruberclone.models.RiderModel
import com.example.rideruberclone.utils.UserUtils
import com.firebase.ui.auth.AuthMethodPickerLayout
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.IdpResponse
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import java.util.Arrays
import java.util.concurrent.TimeUnit

class SplashScreenActivity : AppCompatActivity() {
    private lateinit var providers: List<AuthUI.IdpConfig>
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var listener: FirebaseAuth.AuthStateListener
    private lateinit var database: FirebaseDatabase
    private lateinit var riderInfoRef: DatabaseReference

    private lateinit var getResult: ActivityResultLauncher<Intent>

    private lateinit var progressBar: ProgressBar

    override fun onStart() {
        super.onStart()
        delaySplashScreen()
    }

    private fun delaySplashScreen() {
        Completable.timer(3, TimeUnit.SECONDS, AndroidSchedulers.mainThread())
            .subscribe() {
                firebaseAuth.addAuthStateListener(listener)
            }
    }

    override fun onStop() {
        if (firebaseAuth != null && listener != null) firebaseAuth.removeAuthStateListener(listener)
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.splash_screen_activity)
        init()
    }

    private fun init() {
        getResult =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                val response = IdpResponse.fromResultIntent(result.data)
                if (result.resultCode == Activity.RESULT_OK) {
                    val user = FirebaseAuth.getInstance().currentUser
                } else {
                    Toast.makeText(this, "" + response?.error, Toast.LENGTH_SHORT).show()
                }
            }

        database = FirebaseDatabase.getInstance()
        riderInfoRef = database.getReference(Constants.RIDER_INFO_REFERENCE)
        providers = Arrays.asList(
            AuthUI.IdpConfig.PhoneBuilder().build(),
            AuthUI.IdpConfig.GoogleBuilder().build()
        )
        firebaseAuth = FirebaseAuth.getInstance()
        listener = FirebaseAuth.AuthStateListener { myFirebaseAuth ->
            val user = myFirebaseAuth.currentUser
            if (user != null) {
                Toast.makeText(
                    this@SplashScreenActivity,
                    "Welcome: " + user.uid,
                    Toast.LENGTH_SHORT
                ).show()
                FirebaseMessaging.getInstance()
                    .token
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val token = task.result
                            UserUtils.updateToken(this@SplashScreenActivity, token)
                            Log.d("TOKEN", token)
                        } else {
                            Toast.makeText(
                                this@SplashScreenActivity,
                                "Failed to get FCM token ${task.exception?.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                checkUserFromFirebase()
            } else {
                showLoginLayout()
            }
        }
    }

    private fun showLoginLayout() {
        val authMethodPickerLayout = AuthMethodPickerLayout.Builder(R.layout.sign_in_layout)
            .setPhoneButtonId(R.id.button_phone_sign_in)
            .setGoogleButtonId(R.id.button_google_sign_in)
            .build()

        val signInIntent = AuthUI.getInstance()
            .createSignInIntentBuilder()
            .setTheme(R.style.Base_Theme_RiderUberClone)
            .setAuthMethodPickerLayout(authMethodPickerLayout)
            .setAvailableProviders(providers)
            .setIsSmartLockEnabled(false)
            .build()

        getResult.launch(signInIntent)
    }

    private fun checkUserFromFirebase() {
        riderInfoRef.child(FirebaseAuth.getInstance().currentUser!!.uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val model = snapshot.getValue(RiderModel::class.java)
                        goToHomeActivity(model)
                    } else {
                        showRegisterLayout()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@SplashScreenActivity, error.message, Toast.LENGTH_LONG)
                        .show()
                }

            })

    }

    private fun showRegisterLayout() {
        val builder = AlertDialog.Builder(this, R.style.Base_Theme_RiderUberClone)
        val itemView = LayoutInflater.from(this).inflate(R.layout.register_layout, null, false)

        val edit_text_name =
            itemView.findViewById<View>(R.id.edit_text_first_name) as TextInputEditText
        val edit_text_last_name =
            itemView.findViewById<View>(R.id.edit_text_last_name) as TextInputEditText
        val edit_text_phone_number =
            itemView.findViewById<View>(R.id.edit_text_phone_number) as TextInputEditText

        val btnContinue = itemView.findViewById<Button>(R.id.button_register)

        if (FirebaseAuth.getInstance().currentUser!!.phoneNumber != null
            && !TextUtils.isDigitsOnly(FirebaseAuth.getInstance().currentUser!!.phoneNumber)
        ) {
            edit_text_phone_number.setText(FirebaseAuth.getInstance().currentUser!!.phoneNumber)
        }

        builder.setView(itemView)
        val dialog = builder.create()
        dialog.show()

        btnContinue.setOnClickListener {
            if (TextUtils.isDigitsOnly(edit_text_name.text.toString())) {
                Toast.makeText(
                    this@SplashScreenActivity,
                    "Please enter a First Name",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener

            } else if (TextUtils.isDigitsOnly(edit_text_last_name.text.toString())) {
                Toast.makeText(
                    this@SplashScreenActivity,
                    "Please enter a Last Name",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener

            } else if (TextUtils.isEmpty(edit_text_phone_number.text.toString())) {
                return@setOnClickListener
            } else {
                val model = RiderModel()
                model.firstName = edit_text_name.text.toString()
                model.lastName = edit_text_last_name.text.toString()
                model.phoneNumber = edit_text_phone_number.text.toString()

                riderInfoRef.child(FirebaseAuth.getInstance().currentUser!!.uid)
                    .setValue(model)
                    .addOnFailureListener {
                        Toast.makeText(
                            this@SplashScreenActivity,
                            "${it.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                        dialog.dismiss()
                        progressBar.visibility = View.GONE
                    }.addOnSuccessListener {
                        Toast.makeText(
                            this@SplashScreenActivity,
                            "Register Successfully!",
                            Toast.LENGTH_SHORT
                        ).show()
                        dialog.dismiss()

                        goToHomeActivity(model)
                        progressBar.visibility = View.GONE

                    }
            }
        }
    }

    private fun goToHomeActivity(model: RiderModel?) {
        Constants.currentRider = model // Don't forget!
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
}