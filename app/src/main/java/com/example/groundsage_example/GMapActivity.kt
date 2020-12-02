package com.example.groundsage_example

import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import com.example.groundsage_example.databinding.ActivityGmapBinding
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.*
import com.google.android.material.snackbar.Snackbar
import com.indooratlas.android.sdk.IALocation
import com.indooratlas.android.sdk.IALocationListener
import com.indooratlas.android.sdk.IALocationManager
import com.indooratlas.android.sdk.IARegion
import com.indooratlas.android.sdk.resources.IAFloorPlan
import com.indooratlas.sdk.groundsage.IAGSManager
import com.indooratlas.sdk.groundsage.IAGSManagerListener
import com.indooratlas.sdk.groundsage.data.IAGSVenueDensity
import com.squareup.picasso.Picasso
import com.squareup.picasso.Target
import java.text.SimpleDateFormat
import java.util.*

class GMapActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var networkViewModel: NetworkViewModel
    private lateinit var binding: ActivityGmapBinding
    private var groundSageMgr: IAGSManager = IAGSManager.getInstance(this)
    private lateinit var mapView: MapView
    private lateinit var gmap: GoogleMap
    private var currentFloor = -999
    private var floorValue: Int = -999
    private var mGroundOverlay: GroundOverlay? = null
    private val groundOverlay = GroundOverlayOptions()
    private val polygons = mutableListOf<Polygon>()
    private val markers = mutableListOf<MarkerOptions>()
    private var locationMarker: Marker? = null
    private var locationOverlay: MarkerOptions? = null
    private var circle: Circle? = null
    private var circleOverlay: CircleOptions? = null
    private lateinit var areaList: List<RecyclerAdapter.AreaRow>
    private lateinit var mapLayout: RelativeLayout
    private lateinit var exitRegionText:TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        floorValue = intent.getIntExtra("floor", -1)
        title = String.format("Floor $floorValue")
        setContentView(R.layout.activity_gmap)
        networkViewModel = ViewModelProvider(this).get(NetworkViewModel::class.java)
        binding = DataBindingUtil.setContentView<ActivityGmapBinding>(this, R.layout.activity_gmap)
        binding.networkViewModel = networkViewModel
        binding.lifecycleOwner = this

        mapLayout = findViewById(R.id.gmapLayout)
        exitRegionText = findViewById(R.id.exitRegionMsg)
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.let {
            Log.d("GMapActivity", "supportActionBar != null")
            it.setDisplayHomeAsUpEnabled(true)
        }
        mapView = findViewById(R.id.gmap)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap?) {
        Log.d("GMapActivity", "onMapReady")
        map?.let {
            gmap = map
            gmap.uiSettings.isMyLocationButtonEnabled = false
            gmap.uiSettings.isTiltGesturesEnabled = false
            gmap.isBuildingsEnabled = false
            drawRegions()
            drawLabel()

            groundSageMgr.startSubscription()
            groundSageMgr.addGroundSageListener(mGroundSageListener)
            groundSageMgr.addIARegionListener(mRegionListener)
            groundSageMgr.addIALocationListener(mLocationListener)
        }
    }

    private fun drawRegions() {
        markers.clear()
        polygons.let {
            it.forEach { p -> p.remove() }
            it.clear()
        }
        areaList = Venue.getFilteredAreaList(floorValue)
        for (i in 1..areaList.size) {
            areaList[i - 1].areaProperty.geometry?.let {
                val rectOptions = PolygonOptions().fillColor(
                    areaList[i - 1].densityProperty?.densityColor?.toInt() ?: Color.BLUE
                ).strokeColor(
                    Color.TRANSPARENT
                )
                for (j in 1..it.size) {
                    rectOptions.add(LatLng(it[j - 1].latitude, it[j - 1].longitude))
                }
                val polygon = gmap.addPolygon(rectOptions)
                polygons.add(polygon)

                getPolygonCenterPoint(rectOptions)?.let { latLng ->
                    markers.add(
                        MarkerOptions().position(latLng)
                            .title(areaList[i - 1].areaProperty.description)
                    )
                }
            }
        }
    }

    private fun drawLabel() {
        for (i in markers) {
            gmap.addMarker(i)
        }
    }

    override fun onResume() {
        mapView.onResume()
        super.onResume()
        Log.d("GMapActivity", "onResume")
        binding.networkViewModel?.networkChangeReceiver.let {
            val broadcastIntent = IntentFilter()
            broadcastIntent.addAction(ConnectivityManager.CONNECTIVITY_ACTION)
            registerReceiver(it, broadcastIntent)
        }

    }

    override fun onPause() {
        mapView.onPause()
        super.onPause()
        Log.d("GMapActivity", "onPause")
        groundSageMgr.stopSubscription()
        groundSageMgr.removeGroundSageListener(mGroundSageListener)
        groundSageMgr.removeIARegionListener(mRegionListener)
        groundSageMgr.removeIALocationListener(mLocationListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
        binding.networkViewModel?.networkChangeReceiver.let {
            unregisterReceiver(it)
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()

    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        finish()
        return super.onSupportNavigateUp()
    }

    private fun getPolygonCenterPoint(polygonPointsList: PolygonOptions): LatLng? {
        val builder: LatLngBounds.Builder = LatLngBounds.Builder()
        for (i in polygonPointsList.points) {
            builder.include(i)
        }
        val bounds: LatLngBounds = builder.build()
        return LatLng(bounds.center.latitude, bounds.center.longitude)
    }

    private fun setGroundOverlay(bitmap: Bitmap, floorPlan: IAFloorPlan) {
        Log.d("GMapActivity", "setGroundOverlay")
        val iaLatLng = floorPlan.center
        val center = LatLng(iaLatLng.latitude, iaLatLng.longitude)
        val bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(bitmap)
        groundOverlay.image(bitmapDescriptor)
            .position(center, floorPlan.widthMeters, floorPlan.heightMeters)
            .bearing(floorPlan.bearing)
        mGroundOverlay = gmap.addGroundOverlay(groundOverlay)
    }

    private fun fetchFloorPlanBitmap(floorPlan: IAFloorPlan) {
        Log.d("GMapActivity", "fetchFloorPlanBitmap ${floorPlan.url}")
        val request = Picasso.get().load(floorPlan.url).into(object : Target {
            override fun onBitmapLoaded(bitmap: Bitmap?, from: Picasso.LoadedFrom?) {
                bitmap?.let {
                    Log.d("GMapActivity", "onBitmapLoaded")
                    setGroundOverlay(it, floorPlan)
                } ?: kotlin.run {
                    val snackBar =
                        Snackbar.make(mapLayout, "Load floorPlan failed", Snackbar.LENGTH_SHORT)
                    snackBar.show()
                }
            }

            override fun onBitmapFailed(e: Exception?, errorDrawable: Drawable?) {
                val snackBar =
                    Snackbar.make(mapLayout, "Download floorPlan failed", Snackbar.LENGTH_SHORT)
                snackBar.show()
            }

            override fun onPrepareLoad(placeHolderDrawable: Drawable?) {
                // NA
            }
        })
    }

    private val mGroundSageListener = object : IAGSManagerListener {
        override fun didReceiveDensity(venueDensity: IAGSVenueDensity) {
            Log.d("GMapActivity", "didReceiveDensity")
            venueDensity.area?.let {
                for (i in 1..it.size) {
                    val item = Venue.getAreaList().first { row ->
                        row.areaProperty.id == it[i - 1].id
                    }
                    item.densityProperty = it[i - 1].densityProperty
                }
                drawRegions()
                drawLabel()
                val currentDateTime = SimpleDateFormat("yyyyMMdd HH:mm:ss", Locale.getDefault()).format(Date())
                Snackbar.make(
                    mapLayout,
                    String.format("Last density update: $currentDateTime"),
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }
    }

    private val mRegionListener = object : IARegion.Listener {
        override fun onEnterRegion(region: IARegion?) {
            Log.d("GMapActivity", "onEnterRegion")
            region?.let {
                exitRegionText.visibility = View.GONE
                if (it.type == IARegion.TYPE_FLOOR_PLAN) {
                    Log.d("GMapActivity", "onEnterRegion TYPE_FLOOR_PLAN")
                    val iaLatLng = it.floorPlan.center
                    val center = LatLng(iaLatLng.latitude, iaLatLng.longitude)
                    gmap.setMinZoomPreference(14F)
                    gmap.moveCamera(CameraUpdateFactory.newLatLng(center))
                    if (it.floorPlan.floorLevel == floorValue) {
                        fetchFloorPlanBitmap(it.floorPlan)
                    }
                } else if(it.type == IARegion.TYPE_VENUE) {
                    for (floorPlan in it.venue.floorPlans) {
                        fetchFloorPlanBitmap(floorPlan)
                    }
                }
            }
        }

        override fun onExitRegion(p0: IARegion?) {
            //Clear blue dot and geofences color
            locationMarker?.remove()
            circle?.remove()

            for (i in 1..areaList.size) {
                areaList[i - 1].areaProperty.geometry?.let {
                    val rectOptions = PolygonOptions().fillColor(Color.BLUE)
                        .strokeColor(Color.TRANSPARENT)
                    for (j in 1..it.size) {
                        rectOptions.add(LatLng(it[j - 1].latitude, it[j - 1].longitude))
                    }
                    val polygon = gmap.addPolygon(rectOptions)
                    polygons.add(polygon)
                }
            }
            exitRegionText.visibility = View.VISIBLE
        }

    }

    private val mLocationListener = object : IALocationListener {
        override fun onLocationChanged(location: IALocation?) {
            Log.d("GMapActivity", "onLocationChanged")
            location?.let {
                val point = LatLng(it.latitude, it.longitude)
                circleOverlay =
                    CircleOptions().center(point).radius(it.accuracy.toDouble()).zIndex(99f)
                        .strokeWidth(3.0f)
                locationMarker?.remove()
                if (it.floorLevel == floorValue) {
                    //show blue dot and blue circle
                    locationOverlay =
                        MarkerOptions().position(point).zIndex(100f).anchor(0.5f, 0.5f)
                            .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_bluedot))
                            .flat(true).rotation(it.bearing)
                    locationMarker = gmap.addMarker(locationOverlay)
                    locationMarker?.isVisible = true
                    circleOverlay?.fillColor(mapView.context.getColor(R.color.ColorCurrentFillCircle))
                    circleOverlay?.strokeColor(mapView.context.getColor(R.color.ColorCurrentStrokeCircle))

                } else {
                    //show grey circle and tell user on other floor
                    circleOverlay?.fillColor(mapView.context.getColor(R.color.ColorOtherFillCircle))
                    circleOverlay?.strokeColor(mapView.context.getColor(R.color.ColorOtherStrokeCircle))
                    locationMarker?.isVisible = false
                    if(currentFloor != it.floorLevel){
                        currentFloor = it.floorLevel
                        Snackbar.make(
                            mapLayout,
                            "Your current floor is ${it.floorLevel}",
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                }
                circle?.remove()
                circle = gmap.addCircle(circleOverlay)
            }
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
            val snackBar = when (status) {
                IALocationManager.STATUS_AVAILABLE -> Snackbar.make(
                    mapLayout,
                    "Status available",
                    Snackbar.LENGTH_SHORT
                )
                IALocationManager.STATUS_LIMITED -> Snackbar.make(
                    mapLayout,
                    "Status limited",
                    Snackbar.LENGTH_SHORT
                )
                IALocationManager.STATUS_OUT_OF_SERVICE -> Snackbar.make(
                    mapLayout,
                    "Status out of service",
                    Snackbar.LENGTH_SHORT
                )
                IALocationManager.STATUS_TEMPORARILY_UNAVAILABLE -> Snackbar.make(
                    mapLayout,
                    "Status temporarily unavailable",
                    Snackbar.LENGTH_SHORT
                )
                else -> Snackbar.make(mapLayout, "Status unknown status", Snackbar.LENGTH_SHORT)
            }
            snackBar.show()
        }
    }
}
