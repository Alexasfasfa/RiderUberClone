package com.example.rideruberclone.activities

import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.rideruberclone.Constants
import com.example.rideruberclone.R
import com.example.rideruberclone.Remote.GoogleAPI
import com.example.rideruberclone.Remote.RetrofitClient
import com.example.rideruberclone.databinding.ActivityRequestDriverBinding
import com.example.rideruberclone.models.SelectedPlaceEvent
import com.example.rideruberclone.utils.UserUtils
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.gms.maps.model.SquareCap
import com.google.android.material.snackbar.Snackbar
import com.google.maps.android.ui.IconGenerator
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.json.JSONObject
import java.lang.StringBuilder

class RequestDriverActivity : AppCompatActivity(), OnMapReadyCallback {
    //Spinning animation
    private var animator: ValueAnimator? = null
    private val DESIRED_NUM_OF_SPINS = 5
    private val DESIRED_SECONDS_PER_ONE_FULL_360_SPIN = 40

    //Effect
    private var lastUserCircle: Circle? = null
    private val duration = 1000
    private var lastPulseAnimator: ValueAnimator? = null

    private lateinit var mMap: GoogleMap
    private lateinit var mapFragment: SupportMapFragment
    private lateinit var binding: ActivityRequestDriverBinding

    private var selectedPlaceEvent: SelectedPlaceEvent? = null
    private lateinit var text_origin: TextView
    private lateinit var text_address_pickup: TextView

    //Routes
    private val compositeDisposable = CompositeDisposable()
    private lateinit var googleAPI: GoogleAPI
    private var blackPolyLine: Polyline? = null
    private var greyPolyLine: Polyline? = null
    private var polylineOptions: PolylineOptions? = null
    private var blackPolyLineOptions: PolylineOptions? = null
    private var polylineList: MutableList<LatLng>? = null
    private var originMarker: Marker? = null
    private var destinationMarker: Marker? = null


    override fun onStart() {
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this)
        }
        super.onStart()
    }

    override fun onStop() {
        compositeDisposable.clear()
        if (EventBus.getDefault().hasSubscriberForEvent(SelectedPlaceEvent::class.java)) {
            EventBus.getDefault().removeStickyEvent(SelectedPlaceEvent::class.java)
            EventBus.getDefault().unregister(this)
        }
        super.onStop()
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onSelectedPlaceEvent(event: SelectedPlaceEvent) {
        selectedPlaceEvent = event
    }

    override fun onDestroy() {
        if (animator != null) animator!!.end()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityRequestDriverBinding.inflate(layoutInflater)
        setContentView(binding.root)

        init()
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap


        drawPath(selectedPlaceEvent!!)

        try {
            val success = googleMap.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(
                    this,
                    R.raw.uber_maps_style
                )
            )
            if (!success) {
                Snackbar.make(
                    mapFragment.requireView(),
                    "Error while loading the map style",
                    Snackbar.LENGTH_LONG
                ).show()
            }
        } catch (e: Exception) {
            Snackbar.make(mapFragment.requireView(), e.message!!, Snackbar.LENGTH_LONG).show()
        }

    }

    private fun drawPath(selectedPlaceEvent: SelectedPlaceEvent) {
        compositeDisposable.add(googleAPI.getDirections(
            "driving",
            "less_driving",
            selectedPlaceEvent.originString, selectedPlaceEvent.destinationString,
            getString(R.string.api_key)
        )
        !!.subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { returnResult ->
                Log.d("API_RETURN", returnResult)
                try {

                    val jsonObject = JSONObject(returnResult)
                    val jsonArray = jsonObject.getJSONArray("routes")
                    for (i in 0 until jsonArray.length()) {
                        val route = jsonArray.getJSONObject(i)
                        val poly = route.getJSONObject("overview_polyline")
                        val polyLine = poly.getString("points")
                        polylineList = Constants.decodePoly(polyLine)
                    }

                    polylineOptions = PolylineOptions()
                    polylineOptions!!.color(Color.GRAY)
                    polylineOptions!!.width(12f)
                    polylineOptions!!.startCap(SquareCap())
                    polylineOptions!!.jointType(JointType.ROUND)
                    polylineOptions!!.addAll(polylineList!!)
                    greyPolyLine = mMap.addPolyline(polylineOptions!!)

                    blackPolyLineOptions = PolylineOptions()
                    blackPolyLineOptions!!.color(Color.BLACK)
                    blackPolyLineOptions!!.width(5f)
                    blackPolyLineOptions!!.startCap(SquareCap())
                    blackPolyLineOptions!!.jointType(JointType.ROUND)
                    blackPolyLineOptions!!.addAll(polylineList!!)
                    blackPolyLine = mMap.addPolyline(blackPolyLineOptions!!)

                    //Animator
                    val valueAnimator = ValueAnimator.ofInt(0, 100)
                    valueAnimator.duration = 1100
                    valueAnimator.repeatCount = ValueAnimator.INFINITE
                    valueAnimator.interpolator = LinearInterpolator()
                    valueAnimator.addUpdateListener { value ->
                        val points = greyPolyLine!!.points
                        val percentValue = valueAnimator.animatedValue.toString().toInt()
                        val size = points.size
                        val newPoints = (size * (percentValue / 100f)).toInt()
                        val p = points.subList(0, newPoints)
                        blackPolyLine!!.points = p
                    }
                    valueAnimator.start()

                    val latLngBound = LatLngBounds.Builder().include(selectedPlaceEvent.origin)
                        .include(selectedPlaceEvent.destination)
                        .build()

                    val objects = jsonArray.getJSONObject(0)
                    val legs = objects.getJSONArray("legs")
                    val legsObject = legs.getJSONObject(0)

                    val time = legsObject.getJSONObject("duration")
                    val duration = time.getString("text")

                    val start_address = legsObject.getString("start_address")
                    val end_address = legsObject.getString("end_address")

                    addOriginMarker(duration, start_address)
                    addDestinationMarker(end_address)

                    mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(latLngBound, 160))
                    mMap.moveCamera(CameraUpdateFactory.zoomTo(mMap.cameraPosition.zoom - 1))

                } catch (e: Exception) {
                    Snackbar.make(mapFragment.requireView(), e.message!!, Snackbar.LENGTH_LONG)
                        .show()
                }
            }
        )
    }

    private fun addDestinationMarker(endAddress: String) {
        val view = layoutInflater.inflate(R.layout.destination_info_window, null, false)

        val text_destination = view.findViewById<View>(R.id.text_destination) as TextView
        text_destination.text = Constants.formatAddress(endAddress)

        val generator = IconGenerator(this)
        generator.setContentView(view)
        generator.setBackground(ColorDrawable(Color.TRANSPARENT))
        val icon = generator.makeIcon()

        destinationMarker = mMap.addMarker(
            MarkerOptions().icon(BitmapDescriptorFactory.fromBitmap(icon))
                .position(selectedPlaceEvent!!.destination)
        )

    }

    private fun addOriginMarker(duration: String, startAddress: String) {

        val view = layoutInflater.inflate(R.layout.origin_info_window, null, false)
        val text_time = view.findViewById<View>(R.id.text_time) as TextView
        text_origin = view.findViewById<View>(R.id.text_origin) as TextView

        text_time.text = Constants.formatDuration(duration)
        text_origin.text = Constants.formatAddress(startAddress)

        val generator = IconGenerator(this)
        generator.setContentView(view)
        generator.setBackground(ColorDrawable(Color.TRANSPARENT))
        val icon = generator.makeIcon()

        originMarker = mMap.addMarker(
            MarkerOptions().icon(BitmapDescriptorFactory.fromBitmap(icon))
                .position(selectedPlaceEvent!!.origin)
        )

    }

    private fun init() {
        googleAPI = RetrofitClient.instance!!.create(GoogleAPI::class.java)

        findViewById<Button>(R.id.button_confirm_uber)
            .setOnClickListener {
                text_address_pickup = findViewById<View>(R.id.confirm_pickup_layout)
                    .findViewById<TextView>(R.id.text_view_address_pickup)
                findViewById<View>(R.id.confirm_pickup_layout).visibility = View.VISIBLE
                findViewById<View>(R.id.confirm_uber_layout).visibility = View.GONE
                setDataPickup()
            }

        findViewById<Button>(R.id.btn_confirm_pickup)
            .setOnClickListener {
                if (mMap == null) return@setOnClickListener
                if (selectedPlaceEvent == null) return@setOnClickListener

                mMap.clear()

                //Tilt
                val cameraPos = CameraPosition.Builder().target(selectedPlaceEvent!!.origin)
                    .tilt(45f)
                    .zoom(16f)
                    .build()

                mMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPos))

                //Start animation
                addMarkerWithPulseAnimation()
            }
    }

    private fun addMarkerWithPulseAnimation() {
        findViewById<View>(R.id.confirm_pickup_layout).visibility = View.GONE
        findViewById<View>(R.id.fill_maps).visibility = View.VISIBLE
        findViewById<View>(R.id.fiding_your_rider_layout).visibility = View.VISIBLE

        originMarker = mMap.addMarker(
            MarkerOptions().icon(BitmapDescriptorFactory.defaultMarker())
                .position(selectedPlaceEvent!!.origin)
        )

        addPulsatingEffect(selectedPlaceEvent!!.origin)

    }

    private fun addPulsatingEffect(origin: LatLng) {
        if (lastPulseAnimator != null) lastPulseAnimator!!.cancel()
        if (lastUserCircle != null) lastUserCircle!!.center = origin
        lastPulseAnimator = Constants.valueAnimate(duration.toLong()) { animation ->
            if (lastUserCircle != null) lastUserCircle!!.radius =
                animation.animatedValue.toString().toDouble() else {
                lastUserCircle = mMap.addCircle(
                    CircleOptions()
                        .center(origin)
                        .radius(animation.animatedValue.toString().toDouble())
                        .strokeColor(Color.WHITE)
                        .fillColor(
                            ContextCompat.getColor(
                                this@RequestDriverActivity,
                                R.color.map_darker
                            )
                        )
                )
            }
        }

        //Start rotating camera
        startMaoCameraSpinningAnimation(mMap.cameraPosition.target)

    }

    private fun startMaoCameraSpinningAnimation(target: LatLng) {
        if (animator != null) animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, (DESIRED_NUM_OF_SPINS * 360.toFloat()))
        animator!!.duration = (DESIRED_SECONDS_PER_ONE_FULL_360_SPIN * 1000).toLong()
        animator!!.interpolator = LinearInterpolator()
        animator!!.startDelay = 100
        animator!!.addUpdateListener { valueAnimator ->
            val newBearingValue = valueAnimator.animatedValue as Float
            mMap.moveCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(target)
                        .zoom(16f)
                        .tilt(45f)
                        .bearing(newBearingValue)
                        .build()
                )
            )
        }

        animator!!.start()

        findNearbyDriver(target)
    }

    private fun findNearbyDriver(target: LatLng) {
        if (Constants.driversFound.size > 0) {
            var min = 0f
            var foundDriver = Constants.driversFound[Constants.driversFound.keys.iterator()
                .next()] // Default found driver is the frist driver

            val currentRiderLocation = Location("")
            currentRiderLocation.latitude = target.latitude
            currentRiderLocation.longitude = target.longitude

            for (key in Constants.driversFound.keys) {

                val driverLocation = Location("")
                driverLocation.latitude = Constants.driversFound[key]!!.geoLocation!!.latitude
                driverLocation.longitude = Constants.driversFound[key]!!.geoLocation!!.longitude

                //First, init min value and found driver if first driver is in list
                if (min == 0f) {
                    min = driverLocation.distanceTo(currentRiderLocation)
                    foundDriver = Constants.driversFound[key]
                } else if (driverLocation.distanceTo(currentRiderLocation) < min) {
                    min = driverLocation.distanceTo(currentRiderLocation)
                    foundDriver = Constants.driversFound[key]
                }
            }
//            Snackbar.make(
//                mapFragment.requireView(), StringBuilder("Found driver: ")
//                    .append(foundDriver!!.driverInfoModel!!.phoneNumber), Snackbar.LENGTH_LONG
//            ).show()

            UserUtils.sendRequestToDriver(this@RequestDriverActivity,
            findViewById<View>(R.id.main_layout),
            foundDriver,
            target)

        } else {
            Snackbar.make(
                mapFragment.requireView(),
                getString(R.string.drivers_not_found),
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    private fun setDataPickup() {
        text_address_pickup.text = if (text_origin != null) text_origin.text else "None"
        mMap.clear()
        addPickupMarker()
    }

    private fun addPickupMarker() {
        val view = layoutInflater.inflate(R.layout.pickup_info_window, null, false)

        val generator = IconGenerator(this)
        generator.setContentView(view)
        generator.setBackground(ColorDrawable(Color.TRANSPARENT))
        val icon = generator.makeIcon()
        originMarker = mMap.addMarker(
            MarkerOptions()
                .icon(BitmapDescriptorFactory.fromBitmap(icon))
                .position(selectedPlaceEvent!!.origin)
        )
    }
}
