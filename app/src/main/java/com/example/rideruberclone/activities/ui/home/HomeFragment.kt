package com.example.rideruberclone.activities.ui.home

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.content.res.Resources
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.rideruberclone.Constants
import com.example.rideruberclone.R
import com.example.rideruberclone.callbacks.FirebaseDriverInfoListener
import com.example.rideruberclone.callbacks.FirebaseFailedListener
import com.example.rideruberclone.databinding.FragmentHomeBinding
import com.example.rideruberclone.models.DriverGeoModel
import com.example.rideruberclone.models.DriverInfoModel
import com.example.rideruberclone.models.GeoQueryModel
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.firebase.geofire.GeoQuery
import com.firebase.geofire.GeoQueryEventListener
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.io.IOException
import java.util.Locale

class HomeFragment : Fragment(), OnMapReadyCallback, FirebaseDriverInfoListener {

    private lateinit var mMap: GoogleMap
    private lateinit var mapFragment: SupportMapFragment

    //Location
    private lateinit var locationCallback: LocationCallback
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    //load driver
    var distance = 1.0
    val LIMIT_RANGE = 10.0
    var previousLocation: Location? = null
    var currentLocation: Location? = null

    var firstTime = true

    //Listener
    private lateinit var firebaseDriverInfoListener: FirebaseDriverInfoListener
    private lateinit var firebaseFailedListener: FirebaseFailedListener

    var cityName = ""

    private var _binding: FragmentHomeBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onDestroy() {
        fusedLocationProviderClient.removeLocationUpdates(locationCallback)
        super.onDestroy()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val homeViewModel =
            ViewModelProvider(this).get(HomeViewModel::class.java)

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        init()
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        mapFragment = childFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        //Request Permissions
        Dexter.withContext(context)
            .withPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            .withListener(object : PermissionListener {
                @SuppressLint("MissingPermission")
                override fun onPermissionGranted(permissions: PermissionGrantedResponse?) {
                    mMap.isMyLocationEnabled = true
                    mMap.uiSettings.isMyLocationButtonEnabled = true
                    mMap.setOnMyLocationButtonClickListener() {
                        fusedLocationProviderClient.lastLocation
                            .addOnFailureListener {
                                Toast.makeText(context, "Error", Toast.LENGTH_SHORT).show()
                            }.addOnSuccessListener { location ->
                                val userLatLng = LatLng(location.latitude, location.longitude)
                                mMap.animateCamera(
                                    CameraUpdateFactory.newLatLngZoom(
                                        userLatLng,
                                        10f
                                    )
                                )
                            }
                        true
                    }

                    val locationButton =
                        (mapFragment.view?.findViewById<View>("1".toInt())?.parent as View)
                            .findViewById<View>("2".toInt())
                    val params = locationButton.layoutParams as RelativeLayout.LayoutParams
                    params.addRule(RelativeLayout.ALIGN_TOP, 0)
                    params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE)
                    params.bottomMargin = 250
                }

                override fun onPermissionDenied(permissions: PermissionDeniedResponse?) {
                    Snackbar.make(requireView(), permissions!!.permissionName, Snackbar.LENGTH_LONG)
                        .show()
                }

                override fun onPermissionRationaleShouldBeShown(
                    p0: PermissionRequest?,
                    p1: PermissionToken?
                ) {

                }

            }).check()

        mMap.uiSettings.isZoomControlsEnabled = true
        try {
            val success = googleMap.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(
                    requireContext(),
                    R.raw.uber_maps_style
                )
            )
            if (!success) {
                Log.d("Google Map", "error")
            }
        } catch (e: Resources.NotFoundException) {
            e.printStackTrace()
        }
    }

    private fun init() {
        firebaseDriverInfoListener = this

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 5000
        ).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationAvailability(p0: LocationAvailability) {
                super.onLocationAvailability(p0)
            }

            override fun onLocationResult(location: LocationResult) {
                super.onLocationResult(location)
                val newPos =
                    LatLng(location.lastLocation!!.latitude, location.lastLocation!!.longitude)
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(newPos, 18f))


                //if user as changed location, calculate and load driver again
                if (firstTime) {
                    previousLocation = location.lastLocation
                    currentLocation = location.lastLocation

                    firstTime = false
                } else {
                    previousLocation = currentLocation
                    currentLocation = location.lastLocation
                }

                if (previousLocation?.distanceTo(currentLocation!!)!! / 1000 <= LIMIT_RANGE) {
                    loadAvailableDrivers()
                }
            }
        }

        fusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(requireContext())
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        fusedLocationProviderClient.requestLocationUpdates(
            locationRequest, locationCallback,
            Looper.getMainLooper()
        )

        loadAvailableDrivers()
    }

    private fun loadAvailableDrivers() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Snackbar.make(requireView(), R.string.permission_required, Snackbar.LENGTH_LONG).show()
            return
        }

        fusedLocationProviderClient.lastLocation
            .addOnFailureListener { e ->
                Snackbar.make(requireView(), e.message!!, Snackbar.LENGTH_LONG).show()
            }.addOnSuccessListener { location ->

                //load all drivers in the city
                val geoCoder = Geocoder(requireContext(), Locale.getDefault())
                val addressList: List<Address>?
                try {
                    addressList = geoCoder.getFromLocation(
                        location.latitude, location.longitude,
                        1
                    )
                    cityName = addressList!![0].locality

                    //Query
                    val driver_location_ref = FirebaseDatabase.getInstance()
                        .getReference(Constants.DRIVERS_LOCATION_REFERENCE)
                        .child(cityName)
                    val gf = GeoFire(driver_location_ref)
                    val geoQuery = gf.queryAtLocation(
                        GeoLocation(location.latitude, location.longitude),
                        distance
                    )
                    geoQuery.removeAllListeners()

                    geoQuery.addGeoQueryEventListener(object : GeoQueryEventListener {
                        override fun onKeyEntered(key: String?, location: GeoLocation?) {
                            Constants.driversFound.add(DriverGeoModel(key!!, location!!))
                        }

                        override fun onKeyExited(key: String?) {

                        }

                        override fun onKeyMoved(key: String?, location: GeoLocation?) {

                        }

                        override fun onGeoQueryReady() {
                            if (distance <= LIMIT_RANGE) {
                                distance++
                                loadAvailableDrivers()
                            } else {
                                distance = 0.0
                                addDriverMarker()
                            }
                        }

                        override fun onGeoQueryError(error: DatabaseError?) {
                            Snackbar.make(requireView(), error!!.message, Snackbar.LENGTH_LONG)
                                .show()
                        }

                    })

                    driver_location_ref.addChildEventListener(object : ChildEventListener {
                        override fun onChildAdded(
                            snapshot: DataSnapshot,
                            previousChildName: String?
                        ) {
                            //Have new driver
                            val geoQueryModel = snapshot.getValue(GeoQueryModel::class.java)
                            val geoLocation =
                                GeoLocation(geoQueryModel!!.l!![0], geoQueryModel.l!![1])
                            val driverGeoModel = DriverGeoModel(snapshot.key, geoLocation)
                            val newDriverLocation = Location("")
                            newDriverLocation.latitude = geoLocation.latitude
                            newDriverLocation.latitude = geoLocation.longitude
                            val newDistance = location.distanceTo(newDriverLocation) / 1000 // in km
                            if (newDistance <= LIMIT_RANGE) {
                                findDriverByKey(driverGeoModel)
                            }

                        }

                        override fun onChildChanged(
                            snapshot: DataSnapshot,
                            previousChildName: String?
                        ) {

                        }

                        override fun onChildRemoved(snapshot: DataSnapshot) {

                        }

                        override fun onChildMoved(
                            snapshot: DataSnapshot,
                            previousChildName: String?
                        ) {

                        }

                        override fun onCancelled(error: DatabaseError) {
                            Snackbar.make(requireView(), error.message, Snackbar.LENGTH_LONG)
                                .show()
                        }

                    })

                } catch (e: IOException) {
                    Snackbar.make(requireView(), R.string.permission_required, Snackbar.LENGTH_LONG)
                        .show()
                }
            }
    }

    private fun addDriverMarker() {
        if (Constants.driversFound.size > 0) {
            Observable.fromIterable(Constants.driversFound)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ driverGeoModel: DriverGeoModel? ->
                    findDriverByKey(driverGeoModel)
                },
                    { t: Throwable? ->
                        Snackbar.make(requireView(), t?.message!!, Snackbar.LENGTH_LONG).show()

                    }
                )

        } else {
            Snackbar.make(
                requireView(),
                getString(R.string.drivers_not_found),
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    private fun findDriverByKey(driverGeoModel: DriverGeoModel?) {
        FirebaseDatabase.getInstance()
            .getReference(Constants.DRIVER_INFO_REFERENCE)
            .child(driverGeoModel?.key!!)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.hasChildren()) {
                        driverGeoModel.driverInfoModel =
                            (snapshot.getValue(DriverInfoModel::class.java))
                        firebaseDriverInfoListener.onDriverInfoLoadSuccess(driverGeoModel)
                    } else {
                        firebaseFailedListener.onFirebaseFailed(getString(R.string.key_not_found) + driverGeoModel.key)

                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    firebaseFailedListener.onFirebaseFailed(error.message)
                }

            })

    }

    override fun onDriverInfoLoadSuccess(driverGeoModel: DriverGeoModel?) {
        //if we already have marker with this key, don't set it again
        if (!Constants.markerList.containsKey(driverGeoModel?.key)) {
            Constants.markerList[driverGeoModel!!.key!!] = mMap.addMarker(
                MarkerOptions()
                    .position(
                        LatLng(
                            driverGeoModel.geoLocation!!.latitude,
                            driverGeoModel.geoLocation!!.longitude
                        )
                    )
                    .flat(true)
                    .title(
                        Constants.buildName(
                            driverGeoModel.driverInfoModel!!.firstName!!,
                            driverGeoModel.driverInfoModel!!.lastName!!
                        )
                    )
                    .snippet(driverGeoModel.driverInfoModel!!.phoneNumber)
                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.car))
            )!!
        } else {
            Toast.makeText(requireContext(), "Error", Toast.LENGTH_LONG).show()
        }

        if (!TextUtils.isEmpty(cityName)) {
            val driverLocation = FirebaseDatabase.getInstance()
                .getReference(Constants.DRIVERS_LOCATION_REFERENCE)
                .child(cityName)
                .child(driverGeoModel?.key!!)
            driverLocation.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.hasChildren()) {
                        if (Constants.markerList.get(driverGeoModel.key) != null) {
                            val marker = Constants.markerList.get(driverGeoModel.key)!!
                            marker.remove()
                            Constants.markerList.remove(driverGeoModel.key)
                            driverLocation.removeEventListener(this)
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Snackbar.make(requireView(), error.message, Snackbar.LENGTH_LONG).show()
                }

            })
        }
    }
}