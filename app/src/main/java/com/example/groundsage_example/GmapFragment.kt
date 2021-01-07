package com.example.groundsage_example

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.groundsage_example.databinding.FragmentGmapBinding
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.*
import com.indooratlas.android.sdk.*
import com.indooratlas.android.sdk.resources.IAFloorPlan
import com.indooratlas.android.sdk.resources.IAVenue
import com.indooratlas.sdk.groundsage.IAGSManager
import com.indooratlas.sdk.groundsage.IAGSManagerListener
import com.indooratlas.sdk.groundsage.data.IAGSVenue
import com.indooratlas.sdk.groundsage.data.IAGSVenueDensity
import com.squareup.picasso.Picasso
import com.squareup.picasso.Target


class GmapFragment : Fragment(), OnMapReadyCallback, GoogleMap.OnMapClickListener,
    IAGSManagerListener, IARegion.Listener,
    IALocationListener, GoogleMap.OnMarkerClickListener {

    private lateinit var mapView: MapView
    private lateinit var gmap: GoogleMap
    private lateinit var groundSageMgr: IAGSManager

    private var currentFloor = -999
    private var floorValue: Int = -999
    private var isSameFloor = false
    private var mGroundOverlay: GroundOverlay? = null
    private val groundOverlay = GroundOverlayOptions()
    private val polygons = mutableListOf<Polygon>()
    private val markers = mutableListOf<MarkerOptions>()
    private val poiMarkers = mutableListOf<MarkerOptions>()
    private var locationMarker: Marker? = null
    private var locationOverlay: MarkerOptions? = null
    private var circle: Circle? = null
    private var circleOverlay: CircleOptions? = null
    private var currentFloorPlan: IAFloorPlan? = null

    private val mDynamicPolygons = mutableListOf<Polygon>()
    private val mDynamicPolygonsOverlap = mutableListOf<Polygon>()
    private val mDynamicPolygonsD1D2 = mutableListOf<Polygon>()

    private lateinit var areaList: List<RecyclerAdapter.AreaRow>
    private lateinit var mapLayout: LinearLayout
    private lateinit var exitRegionText: TextView
    private lateinit var logText: TextView
    private lateinit var dynamicGeo: Switch
    private lateinit var dynamicGeoOverlap: Switch
    private lateinit var dynamicGeoD1D2: Switch
    private lateinit var appStatusViewModel: AppStatusViewModel
    private lateinit var binding: FragmentGmapBinding

    private var wayfindingMarker: Marker? = null
    private var selectedPoiMarker: Marker? = null
    private var polylines = mutableListOf<Polyline>()
    private var mWayfindingDestination: IAWayfindingRequest? = null
    private var mCurrentRoute: IARoute? = null
    private var mVenue: IAVenue? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_gmap, container, false)
        activity?.let {
            appStatusViewModel = ViewModelProvider(it).get(AppStatusViewModel::class.java)
        }

        binding.appStatusViewModel = appStatusViewModel
        binding.lifecycleOwner = this
        mapLayout = binding.root.findViewById(R.id.fragmentLayout)
        exitRegionText = binding.root.findViewById(R.id.exitRegionMsg)
        logText = binding.root.findViewById(R.id.logText)
        logText.movementMethod = ScrollingMovementMethod()
        dynamicGeo = binding.root.findViewById(R.id.dynamicGeo)
        dynamicGeoOverlap = binding.root.findViewById(R.id.dynamicGeoOverlap)
        dynamicGeoD1D2 = binding.root.findViewById(R.id.dynamicD1D2)
        mapView = binding.root.findViewById(R.id.gmap)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

        return binding.root
    }

    private val geofenceListener = IAGeofenceListener { geofenceEvent ->
        var geoNameList = ""
        if (geofenceEvent.geofenceTransition == IAGeofence.GEOFENCE_TRANSITION_ENTER) {
            Log.d("GmapFragment", "GEOFENCE_TRANSITION_ENTER")

            geofenceEvent.triggeringGeofences.forEach { geo ->
                when (geo.id) {
                    DynamicGeofence.dynamicGeoID -> {
                        mDynamicPolygons.forEach {
                            it.strokeColor = Color.BLUE
                        }
                    }
                    DynamicGeofence.dynamicGeoOverlapID -> {
                        mDynamicPolygonsOverlap.forEach {
                            it.strokeColor = Color.BLUE
                        }
                    }
                    DynamicGeofence.dynamicGeoD1ID -> {
                        mDynamicPolygonsD1D2[0].strokeColor = Color.BLUE
                    }
                    DynamicGeofence.dynamicGeoD2ID -> {
                        mDynamicPolygonsD1D2[1].strokeColor = Color.BLUE
                    }
                }
                geoNameList = geoNameList + geo.name + ", "
            }
            logText.append("${appStatusViewModel.getCurrentDateTime()} Enter $geoNameList \n")

        } else {
            Log.d("GmapFragment", "GEOFENCE_TRANSITION_EXIT")
            geofenceEvent.triggeringGeofences.forEach { geo ->
                when (geo.id) {
                    DynamicGeofence.dynamicGeoID -> {
                        mDynamicPolygons.forEach { polygon ->
                            context?.let { it1 ->
                                polygon.strokeColor =
                                    ContextCompat.getColor(it1, R.color.colorExtraGeo)
                            } ?: kotlin.run { polygon.strokeColor = Color.CYAN }
                        }
                    }
                    DynamicGeofence.dynamicGeoOverlapID -> {
                        mDynamicPolygonsOverlap.forEach { polygon ->
                            context?.let { it1 ->
                                polygon.strokeColor =
                                    ContextCompat.getColor(it1, R.color.colorExtraGeoOverlap)
                            } ?: kotlin.run { polygon.strokeColor = Color.MAGENTA }
                        }
                    }
                    DynamicGeofence.dynamicGeoD1ID -> {
                        context?.let { it1 ->
                            mDynamicPolygonsD1D2[0].strokeColor =
                                ContextCompat.getColor(it1, R.color.colorExtraGeoD1D2)
                        } ?: kotlin.run { mDynamicPolygonsD1D2[0].strokeColor = Color.DKGRAY }
                    }
                    DynamicGeofence.dynamicGeoD2ID -> {
                        context?.let { it1 ->
                            mDynamicPolygonsD1D2[1].strokeColor =
                                ContextCompat.getColor(it1, R.color.colorExtraGeoD1D2)
                        } ?: kotlin.run { mDynamicPolygonsD1D2[1].strokeColor = Color.DKGRAY }
                    }
                }
                geoNameList = geoNameList + geo.name + ", "
            }
            logText.append("${appStatusViewModel.getCurrentDateTime()} Exit $geoNameList \n")
        }
    }

    private val wayfindingListener = IAWayfindingListener {
        mCurrentRoute = it
        if (hasArrivedToDestination(it)) {
            //stop wayfinding
            mCurrentRoute = null
            mWayfindingDestination = null
            groundSageMgr.removeWayfindingUpdates()
            wayfindingMarker?.remove()
            wayfindingMarker = null
            selectedPoiMarker = null
            logText.append("${appStatusViewModel.getCurrentDateTime()} Wayfinding: arrived \n")
        }
        updateRouteVisualization()

    }

    private fun updateRouteVisualization() {
        polylines.forEach {
            it.remove()
        }
        polylines.clear()

        if (mCurrentRoute == null) {
            return
        }

        val polylineOptions = PolylineOptions()
        mCurrentRoute?.legs?.size?.let { size ->
            if (size > 0) {
                mCurrentRoute?.legs?.forEach { leg ->
                    if (leg.edgeIndex != null) {
                        polylineOptions.add(LatLng(leg.begin.latitude, leg.begin.longitude))
                        polylineOptions.add(LatLng(leg.end.latitude, leg.end.longitude))
                    }
                    if (leg.begin.floor == floorValue && leg.end.floor == floorValue) {
                        polylineOptions.color(Color.BLUE)
                    } else {
                        polylineOptions.color(Color.GRAY)
                    }
                }
            } else {
                polylineOptions.color(Color.CYAN)
                locationMarker?.position?.let { currentPosition ->
                    mWayfindingDestination?.let { destination ->
                        polylineOptions.add(currentPosition)
                        polylineOptions.add(LatLng(destination.latitude, destination.longitude))
                    }
                }
            }
        }

        polylineOptions.zIndex(95f)
        polylines.add(gmap.addPolyline(polylineOptions))
    }

    private fun hasArrivedToDestination(route: IARoute): Boolean {
        if (route.legs.size == 0) {
            return false
        }
        val FINISH_THRESHOLD_METERS = 8.0
        var routeLength: Double = 0.0
        route.legs.forEach {
            routeLength += it.length
        }
        return routeLength < FINISH_THRESHOLD_METERS
    }

    private fun setWayfindingTarget(point: LatLng, addMarker: Boolean) {
        mWayfindingDestination =
            IAWayfindingRequest.Builder().withFloor(floorValue).withLatitude(point.latitude)
                .withLongitude(point.longitude).build()
        mWayfindingDestination?.let {
            groundSageMgr.requestWayfindingUpdates(it, wayfindingListener)
        }

        if (wayfindingMarker != null) {
            wayfindingMarker?.remove()
            wayfindingMarker = null
        }
        if (addMarker) {
            wayfindingMarker = gmap.addMarker(
                MarkerOptions().position(point)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
            )
        }
        logText.append(
            "${appStatusViewModel.getCurrentDateTime()} Wayfinding: set destination ${mWayfindingDestination?.latitude} ${mWayfindingDestination?.longitude}\n" +
                    "floor: ${mWayfindingDestination?.floor}\n"
        )
    }

    override fun onMapReady(map: GoogleMap?) {
        Log.d("GmapFragment", "onMapReady")
        appStatusViewModel.selectedFloorLevel.value?.let {
            floorValue = it
        }

        map?.let {
            gmap = map
            gmap.uiSettings.isMyLocationButtonEnabled = false
            gmap.uiSettings.isTiltGesturesEnabled = false
            gmap.isBuildingsEnabled = false

            appStatusViewModel.region.value?.let {
                Log.d("GmapFragment", "onMapReady get region")
                val iaLatLng = it.floorPlan.center
                val center = LatLng(iaLatLng.latitude, iaLatLng.longitude)

                gmap.moveCamera(CameraUpdateFactory.newLatLngZoom(center, 22F))
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

            //add cloud geofences callback
            initGeofence()
            if (floorValue == 19) {
                //add dynamic geofences callback when switch is on
                initDynamicSwitch()
            }
            appStatusViewModel.region.value?.let {
                onEnterRegion(it)
            }
            gmap.setOnMapClickListener(this)
            gmap.setOnMarkerClickListener(this)
        }
    }

    private fun initGeofence() {
        Log.d("GmapFragment", "initGeofence")
        val geofenceRequest =
            IAGeofenceRequest.Builder().withCloudGeofences(true).build()
        groundSageMgr.addGeofences(
            geofenceRequest, geofenceListener
        )
    }

    private fun initExtraGeoFence(
        geoSwitch: Switch,
        extraGeofence: IAGeofence,
        polygons: MutableList<Polygon>,
        color: Int,
        geofenceID: String
    ) {
        geoSwitch.visibility = View.VISIBLE
        geoSwitch.setOnClickListener {
            if (geoSwitch.isChecked) {
                val newRequest =
                    IAGeofenceRequest.Builder().withCloudGeofences(true).withGeofence(extraGeofence)
                        .build()
                groundSageMgr.addGeofences(
                    newRequest, geofenceListener
                )
                drawIARegions(polygons, arrayListOf(extraGeofence), color)
            } else {
                groundSageMgr.removeGeofences(arrayListOf(geofenceID))
                clearIARegions(polygons)
            }
        }
    }

    private fun initExtraGeoFenceList(
        geoSwitch: Switch,
        extraGeofencelist: List<IAGeofence>,
        polygons: MutableList<Polygon>,
        color: Int,
        geofenceIDlist: ArrayList<String>
    ) {
        geoSwitch.visibility = View.VISIBLE
        geoSwitch.setOnClickListener {
            if (geoSwitch.isChecked) {
                val newRequest =
                    IAGeofenceRequest.Builder().withCloudGeofences(true)
                        .withGeofences(extraGeofencelist).build()
                groundSageMgr.addGeofences(
                    newRequest, geofenceListener
                )
                drawIARegions(polygons, extraGeofencelist, color)
            } else {
                groundSageMgr.removeGeofences(geofenceIDlist)
                clearIARegions(polygons)
            }
        }
    }

    private fun initDynamicSwitch() {
        context?.let {
            initExtraGeoFence(
                dynamicGeo,
                DynamicGeofence.geofenceDynamic,
                mDynamicPolygons,
                ContextCompat.getColor(it, R.color.colorExtraGeo),
                DynamicGeofence.dynamicGeoID
            )
            initExtraGeoFence(
                dynamicGeoOverlap,
                DynamicGeofence.geofenceDynamicOverlap,
                mDynamicPolygonsOverlap,
                ContextCompat.getColor(it, R.color.colorExtraGeoOverlap),
                DynamicGeofence.dynamicGeoOverlapID
            )
            initExtraGeoFenceList(
                dynamicGeoD1D2,
                arrayListOf(DynamicGeofence.geofenceDynamicD1, DynamicGeofence.geofenceDynamicD2),
                mDynamicPolygonsD1D2,
                ContextCompat.getColor(it, R.color.colorExtraGeoD1D2),
                arrayListOf(DynamicGeofence.dynamicGeoD1ID, DynamicGeofence.dynamicGeoD2ID)
            )
        } ?: kotlin.run {
            initExtraGeoFence(
                dynamicGeo,
                DynamicGeofence.geofenceDynamic,
                mDynamicPolygons,
                Color.CYAN,
                DynamicGeofence.dynamicGeoID
            )
            initExtraGeoFence(
                dynamicGeoOverlap,
                DynamicGeofence.geofenceDynamicOverlap,
                mDynamicPolygonsOverlap,
                Color.MAGENTA,
                DynamicGeofence.dynamicGeoOverlapID
            )
            initExtraGeoFenceList(
                dynamicGeoD1D2,
                arrayListOf(DynamicGeofence.geofenceDynamicD1, DynamicGeofence.geofenceDynamicD2),
                mDynamicPolygonsD1D2,
                Color.DKGRAY,
                arrayListOf(DynamicGeofence.dynamicGeoD1ID, DynamicGeofence.dynamicGeoD2ID)
            )
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

    fun clearIARegions(polygons: MutableList<Polygon>) {
        polygons.forEach {
            it.remove()
        }
        polygons.clear()
    }

    fun drawIARegions(
        polygons: MutableList<Polygon>,
        geofenceList: List<IAGeofence>,
        color: Int
    ) {

        geofenceList.forEach { geo ->
            val rectOptions = PolygonOptions().fillColor(
                Color.TRANSPARENT
            ).strokeColor(
                color
            ).strokeWidth(5f)

            geo.edges.forEach { point ->
                rectOptions.add(LatLng(point[0], point[1]))
            }
            val polygon = gmap.addPolygon(rectOptions)
            polygons.add(polygon)
        }
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

    private fun setPois(iaVenue:IAVenue){
        logText.append("${appStatusViewModel.getCurrentDateTime()} setPois poi count: ${iaVenue.poIs.size}\n")
        poiMarkers.clear()
        iaVenue.poIs?.forEach { it ->
            logText.append("${appStatusViewModel.getCurrentDateTime()} poi name: ${it.name} floor: ${it.floor}\n")
            if (it.floor == currentFloor){
                poiMarkers.add(
                        MarkerOptions().position(LatLng(it.location.latitude, it.location.longitude))
                            .title(it.name).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                    )
            }
            poiMarkers.forEach {
                gmap.addMarker(it)
            }

        }
    }

    private fun drawLabel() {
        markers.forEach {
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
                    currentFloorPlan = floorPlan
                } ?: kotlin.run {
                    logText.append("${appStatusViewModel.getCurrentDateTime()} Load floorPlan failed \n")
                }
            }

            override fun onBitmapFailed(e: Exception?, errorDrawable: Drawable?) {
                logText.append("${appStatusViewModel.getCurrentDateTime()} Download floorPlan failed \n")
            }

            override fun onPrepareLoad(placeHolderDrawable: Drawable?) {
                // NA
            }
        })
    }

    override fun onEnterDensityRegion(region: IARegion, venue: IAGSVenue) {
        Log.d("GmapFragment", "onEnterDensityRegion")
        logText.append("${appStatusViewModel.getCurrentDateTime()} Enter density region \n" +
                "name: ${region.name}\n" +
                "region id: ${region.id} \n" +
                "venue id: ${region.venue.id}\n\n")
    }

    override fun onExitDensityRegion(region: IARegion, venue: IAGSVenue) {
        Log.d("GmapFragment", "onExitDensityRegion")
        logText.append(
            "${appStatusViewModel.getCurrentDateTime()} Exit density region\n" +
                    "name: ${region.name} \n" +
                    "region id: ${region.id} \n" +
                    "venue id: ${region.venue.id}\n\n"
        )
    }

    override fun onUpdateDensity(venueDensity: IAGSVenueDensity?) {
        Log.d("GmapFragment", "onUpdateDensity")
        logText.append("${appStatusViewModel.getCurrentDateTime()} Update density\n")
        venueDensity?.area?.let {
            logText.append("${appStatusViewModel.getCurrentDateTime()} onUpdateDensity area size: ${venueDensity.area?.size}\n")
            for (i in 1..it.size) {
                val item = Venue.getAreaList().first { row ->
                    row.areaProperty.id == it[i - 1].areaId
                }
                item.densityProperty = it[i - 1].densityProperty
            }
            drawRegions()
            drawLabel()
        } ?: kotlin.run {
            logText.append("${appStatusViewModel.getCurrentDateTime()} onUpdateDensity no are\n")
        }
    }

    override fun onEnterRegion(region: IARegion?) {
        Log.d("GmapFragment", "onEnterRegion")

        exitRegionText.visibility = View.GONE
        region?.let {
            if (it.type == IARegion.TYPE_FLOOR_PLAN) {
                logText.append("${appStatusViewModel.getCurrentDateTime()} Enter IA floorplan: ${region?.name} \n")
                appStatusViewModel.region.postValue(it)
                currentFloor = it.floorPlan.floorLevel
                mVenue?.let { venue -> setPois(venue) }
                isSameFloor = it.floorPlan.floorLevel == floorValue
                Log.d("GmapFragment", "onEnterRegion TYPE_FLOOR_PLAN")
                val iaLatLng = it.floorPlan.center
                val center = LatLng(iaLatLng.latitude, iaLatLng.longitude)
                gmap.moveCamera(CameraUpdateFactory.newLatLngZoom(center, 22F))
                if (isSameFloor) {
                    Log.d("GmapFragment", "start fetchFloorPlanBitmap")
                    fetchFloorPlanBitmap(it.floorPlan)
                }
            } else if (it.type == IARegion.TYPE_VENUE) {
                mVenue = it.venue
                logText.append("${appStatusViewModel.getCurrentDateTime()} Enter IA region\n" +
                        "name: ${region.name} \n" +
                        "region id: ${region.id} \n" +
                        "venue id: ${region.venue.id}\n\n")
            }
        }
    }

    override fun onExitRegion(region: IARegion?) {
        //Clear blue dot and geofences color
        region?.let {
            if (it.type == IARegion.TYPE_VENUE) {
                logText.append("${appStatusViewModel.getCurrentDateTime()} Exit IA region\n" +
                        "name: ${region.name} \n" +
                        "region id: ${region.id} \n" +
                        "venue id: ${region.venue.id}\n\n")
                exitRegionText.visibility = View.VISIBLE
                locationMarker?.remove()
                poiMarkers.clear()
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
            } else if (it.type == IARegion.TYPE_FLOOR_PLAN) {
                logText.append("${appStatusViewModel.getCurrentDateTime()} Exit IA floorplan: ${region.name} \n")
            }
        }
    }

    override fun onLocationChanged(location: IALocation?) {
        if (currentFloorPlan == null && isSameFloor) {
            Log.d("GmapFragment", "start fetchFloorPlanBitmap")
            appStatusViewModel.region.value?.let { fetchFloorPlanBitmap(it.floorPlan) }
        }
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
                if (currentFloor != it.floorLevel) {
                    currentFloor = it.floorLevel
                    logText.append("${appStatusViewModel.getCurrentDateTime()} Your current floor is ${it.floorLevel}\n")
                }
            }
            circle?.remove()
            circle = gmap.addCircle(circleOverlay)
        }
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        when (status) {
            IALocationManager.STATUS_AVAILABLE -> logText.append("${appStatusViewModel.getCurrentDateTime()} Status STATUS_AVAILABLE\n")
            IALocationManager.STATUS_LIMITED -> logText.append("${appStatusViewModel.getCurrentDateTime()} Status STATUS_LIMITED\n")
            IALocationManager.STATUS_OUT_OF_SERVICE -> logText.append("${appStatusViewModel.getCurrentDateTime()} Status STATUS_OUT_OF_SERVICE\n")
            IALocationManager.STATUS_TEMPORARILY_UNAVAILABLE -> logText.append("${appStatusViewModel.getCurrentDateTime()} Status STATUS_TEMPORARILY_UNAVAILABLE\n")
            else -> logText.append("${appStatusViewModel.getCurrentDateTime()} Status unknown status\n")
        }
    }

    override fun onMapClick(point: LatLng?) {
        point?.let {
            if (wayfindingMarker == null && poiMarkers.isEmpty()) {
                setWayfindingTarget(it, true)
            }
        }
    }

    override fun onMarkerClick(marker: Marker?): Boolean {
        if (marker == wayfindingMarker){
            mCurrentRoute = null
            mWayfindingDestination = null
            groundSageMgr.removeWayfindingUpdates()
            updateRouteVisualization()
            logText.append("${appStatusViewModel.getCurrentDateTime()} Wayfinding cancel\n")
            wayfindingMarker?.remove()
            wayfindingMarker = null
            return false
        }

        if (selectedPoiMarker == null){
            marker?.let {
                selectedPoiMarker = it
                setWayfindingTarget(it.position, false)
            }
        } else {
            if (marker == selectedPoiMarker) {
                mCurrentRoute = null
                mWayfindingDestination = null
                groundSageMgr.removeWayfindingUpdates()
                updateRouteVisualization()
                logText.append("${appStatusViewModel.getCurrentDateTime()} Wayfinding cancel\n")
                selectedPoiMarker = null
            }
        }

        return false
    }
}
