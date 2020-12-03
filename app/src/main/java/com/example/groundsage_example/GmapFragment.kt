package com.example.groundsage_example

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.groundsage_example.databinding.FragmentGmapBinding
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


class GmapFragment : Fragment(), OnMapReadyCallback, IAGSManagerListener, IARegion.Listener,
    IALocationListener {

    private lateinit var mapView: MapView
    private lateinit var gmap: GoogleMap
    private lateinit var groundSageMgr: IAGSManager

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
    private lateinit var mapLayout: FrameLayout
    private lateinit var exitRegionText: TextView

    private lateinit var networkViewModel: NetworkViewModel
    private lateinit var binding: FragmentGmapBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_gmap, container,false)
        activity?.let {
            networkViewModel = ViewModelProvider(it).get(NetworkViewModel::class.java)
        }

        binding.networkViewModel = networkViewModel
        binding.lifecycleOwner = this
        mapLayout = binding.root.findViewById(R.id.fragmentLayout)
        exitRegionText = binding.root.findViewById(R.id.exitRegionMsg)
        mapView = binding.root.findViewById(R.id.gmap)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

        return binding.root
    }

    companion object {

    }

    override fun onMapReady(map: GoogleMap?) {
        Log.d("GmapFragment", "onMapReady")
        networkViewModel.selectedFloorLevel.value?.let {
            floorValue = it
        }
        map?.let {
            gmap = map
            gmap.uiSettings.isMyLocationButtonEnabled = false
            gmap.uiSettings.isTiltGesturesEnabled = false
            gmap.isBuildingsEnabled = false

            networkViewModel.region.value?.let {
                Log.d("GmapFragment", "onMapReady get region")
                val iaLatLng = it.floorPlan.center
                val center = LatLng(iaLatLng.latitude, iaLatLng.longitude)
                gmap.setMinZoomPreference(22F)
                gmap.moveCamera(CameraUpdateFactory.newLatLng(center))
                if (it.floorPlan.floorLevel == floorValue) {
                    fetchFloorPlanBitmap(it.floorPlan)
                }
            }
            drawRegions()
            drawLabel()
            context?.let {
                groundSageMgr = IAGSManager.getInstance(it)
                groundSageMgr.addGroundSageListener(this)
                groundSageMgr.addIARegionListener(this)
                groundSageMgr.addIALocationListener(this)
            }
        }
    }

    override fun onResume() {
        mapView.onResume()
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    private fun drawRegions() {
        markers.clear()
        polygons.let {
            it.forEach { p -> p.remove() }
            it.clear()
        }
        areaList = Venue.getFilteredAreaList(floorValue)
        areaList.forEach { area ->
            area.areaProperty.geometry?.let {
                val rectOptions = PolygonOptions().fillColor(
                    area.densityProperty?.densityColor?.toInt() ?: Color.BLUE
                ).strokeColor(
                    Color.TRANSPARENT
                )
                it.forEach { point ->
                    rectOptions.add(LatLng(point.latitude, point.longitude))
                }
                val polygon = gmap.addPolygon(rectOptions)
                polygons.add(polygon)

                getPolygonCenterPoint(rectOptions)?.let { latLng ->
                    markers.add(
                        MarkerOptions().position(latLng)
                            .title(area.areaProperty.description)
                    )
                }
            }
        }
    }

    private fun drawLabel() {
        markers.forEach{
            gmap.addMarker(it)
        }
    }

    private fun getPolygonCenterPoint(polygonPointsList: PolygonOptions): LatLng? {
        val builder: LatLngBounds.Builder = LatLngBounds.Builder()
        polygonPointsList.points.forEach {
            builder.include(it)
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
        Log.d("GmapFragment", "fetchFloorPlanBitmap ${floorPlan.url}")
        Picasso.get().load(floorPlan.url).into(object : Target {
            override fun onBitmapLoaded(bitmap: Bitmap?, from: Picasso.LoadedFrom?) {
                bitmap?.let {
                    Log.d("GmapFragment", "onBitmapLoaded")
                    setGroundOverlay(it, floorPlan)
                } ?: kotlin.run {
                    val snackBar =
                        Snackbar.make(mapLayout, "Load floorPlan failed", Snackbar.LENGTH_LONG)
                    snackBar.show()
                }
            }

            override fun onBitmapFailed(e: Exception?, errorDrawable: Drawable?) {
                val snackBar =
                    Snackbar.make(mapLayout, "Download floorPlan failed", Snackbar.LENGTH_LONG)
                snackBar.show()
            }

            override fun onPrepareLoad(placeHolderDrawable: Drawable?) {
                // NA
            }
        })
    }

    override fun didReceiveDensity(venueDensity: IAGSVenueDensity) {
        Log.d("GmapFragment", "didReceiveDensity")
        venueDensity.area?.let {
            for (i in 1..it.size) {
                val item = Venue.getAreaList().first { row ->
                    row.areaProperty.id == it[i - 1].id
                }
                item.densityProperty = it[i - 1].densityProperty
            }
            drawRegions()
            drawLabel()
        }
    }

    override fun onEnterRegion(region: IARegion?) {
        Log.d("GmapFragment", "onEnterRegion")
        exitRegionText.visibility = View.GONE
        region?.let {
            if (it.type == IARegion.TYPE_FLOOR_PLAN) {
                Log.d("GmapFragment", "onEnterRegion TYPE_FLOOR_PLAN")
                val iaLatLng = it.floorPlan.center
                val center = LatLng(iaLatLng.latitude, iaLatLng.longitude)
                gmap.setMinZoomPreference(22F)
                gmap.moveCamera(CameraUpdateFactory.newLatLng(center))
                if (it.floorPlan.floorLevel == floorValue) {
                    fetchFloorPlanBitmap(it.floorPlan)
                }
            }
        }
    }

    override fun onExitRegion(p0: IARegion?) {
        //Clear blue dot and geofences color
        exitRegionText.visibility = View.GONE
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
    }

    override fun onLocationChanged(location: IALocation?) {
        Log.d("GmapFragment", "onLocationChanged")
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
            else -> Snackbar.make(mapLayout, "Status unknown status", Snackbar.LENGTH_LONG)
        }
        snackBar.show()
    }
}
