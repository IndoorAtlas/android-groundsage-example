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
import android.widget.TableRow
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
import kotlin.math.floor


class GmapFragment : Fragment(), OnMapReadyCallback, GoogleMap.OnMapClickListener,
    IAGSManagerListener, IARegion.Listener,
    IALocationListener, GoogleMap.OnMarkerClickListener {

    private lateinit var mapView: MapView
    private lateinit var gmap: GoogleMap
    private lateinit var groundSageMgr: IAGSManager
    private var locateMe = false
    private var currentFloor = -999
    private var floorValue: Int = -999
    private var isSameFloor = false
    private var mGroundOverlay: GroundOverlay? = null
    private val groundOverlay = GroundOverlayOptions()
    private val polygons = mutableListOf<Polygon>()
    private val geofenceMarkerOverlays = mutableListOf<MarkerOptions>()
    private val geofenceMarkers = mutableListOf<Marker>()
    private val poiMarkers = mutableListOf<Marker>()
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
        val polylineOptions = PolylineOptions()

        if (mCurrentRoute == null) {
            return
        } else {

            mCurrentRoute?.legs?.forEach { leg ->
                if (leg.edgeIndex != null) {
                    polylineOptions.add(LatLng(leg.begin.latitude, leg.begin.longitude))
                    polylineOptions.add(LatLng(leg.end.latitude, leg.end.longitude))
                }
                if (locateMe || mWayfindingDestination?.floor == currentFloor) {
                    context?.let {
                        polylineOptions.color(ContextCompat.getColor(it, R.color.colorWayfinding))
                    }
                } else {
                    polylineOptions.color(Color.GRAY)
                }
            }
            mCurrentRoute?.let{
                if (it.legs.size <= 0){
                    circle?.center?.let { currentPosition ->
                        mWayfindingDestination?.let { destination ->
                            polylineOptions.add(currentPosition)
                            polylineOptions.add(LatLng(destination.latitude, destination.longitude))
                        }
                    }
                    polylineOptions.color(Color.CYAN)
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

    private fun setWayfindingTarget(point: LatLng, addMarker: Boolean, floor: Int) {
        mWayfindingDestination = IAWayfindingRequest.Builder()
            .withFloor(floor)
            .withLatitude(point.latitude)
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
        appStatusViewModel.selectedFloorLevel.value?.let {
            floorValue = it
            locateMe = false
        } ?: kotlin.run {
            locateMe = true
        }

        map?.let {
            gmap = map
            gmap.uiSettings.isMyLocationButtonEnabled = false
            gmap.uiSettings.isTiltGesturesEnabled = false
            gmap.isBuildingsEnabled = false



            drawRegions()
            drawLabel()
            context?.let {
                groundSageMgr = IAGSManager.getInstance(it)
                groundSageMgr.addGroundSageListener(this)
                groundSageMgr.registerRegionListener(this)
                groundSageMgr.registerLocationListener(this)
            }
            //add cloud geofences callback
            initGeofence()

            appStatusViewModel.venueRegion.value?.let {
                onEnterRegion(it)
            }

            appStatusViewModel.floorplanRegion.value?.let {
                onEnterRegion(it)
            }
            gmap.setOnMapClickListener(this)
            gmap.setOnMarkerClickListener(this)
        }
    }

    private fun initGeofence() {
        val geofenceRequest =
            IAGeofenceRequest.Builder().withCloudGeofences(true).build()
        groundSageMgr.addGeofences(
            geofenceRequest, geofenceListener
        )
    }

    private fun initExtraGeoFence(
        enabled: Boolean,
        geoSwitch: Switch,
        extraGeofence: IAGeofence,
        polygons: MutableList<Polygon>,
        color: Int,
        geofenceID: String
    ) {
        if (enabled) {
            geoSwitch.visibility = View.VISIBLE
            geoSwitch.setOnClickListener {
                if (geoSwitch.isChecked) {
                    val newRequest =
                        IAGeofenceRequest.Builder().withCloudGeofences(true)
                            .withGeofence(extraGeofence)
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
        } else {
            geoSwitch.visibility = View.GONE
            groundSageMgr.removeGeofences(arrayListOf(geofenceID))
            clearIARegions(polygons)
        }
    }

    private fun initExtraGeoFenceList(
        enabled: Boolean,
        geoSwitch: Switch,
        extraGeofencelist: List<IAGeofence>,
        polygons: MutableList<Polygon>,
        color: Int,
        geofenceIDlist: ArrayList<String>
    ) {
        if (enabled) {
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
        } else {
            geoSwitch.visibility = View.GONE
            groundSageMgr.removeGeofences(geofenceIDlist)
            clearIARegions(polygons)
        }

    }

    private fun initDynamicSwitch(enabled: Boolean) {
        context?.let {
            initExtraGeoFence(
                enabled,
                dynamicGeo,
                DynamicGeofence.geofenceDynamic,
                mDynamicPolygons,
                ContextCompat.getColor(it, R.color.colorExtraGeo),
                DynamicGeofence.dynamicGeoID
            )
            initExtraGeoFence(
                enabled,
                dynamicGeoOverlap,
                DynamicGeofence.geofenceDynamicOverlap,
                mDynamicPolygonsOverlap,
                ContextCompat.getColor(it, R.color.colorExtraGeoOverlap),
                DynamicGeofence.dynamicGeoOverlapID
            )
            initExtraGeoFenceList(
                enabled,
                dynamicGeoD1D2,
                arrayListOf(DynamicGeofence.geofenceDynamicD1, DynamicGeofence.geofenceDynamicD2),
                mDynamicPolygonsD1D2,
                ContextCompat.getColor(it, R.color.colorExtraGeoD1D2),
                arrayListOf(DynamicGeofence.dynamicGeoD1ID, DynamicGeofence.dynamicGeoD2ID)
            )
        } ?: kotlin.run {
            initExtraGeoFence(
                enabled,
                dynamicGeo,
                DynamicGeofence.geofenceDynamic,
                mDynamicPolygons,
                Color.CYAN,
                DynamicGeofence.dynamicGeoID
            )
            initExtraGeoFence(
                enabled,
                dynamicGeoOverlap,
                DynamicGeofence.geofenceDynamicOverlap,
                mDynamicPolygonsOverlap,
                Color.MAGENTA,
                DynamicGeofence.dynamicGeoOverlapID
            )
            initExtraGeoFenceList(
                enabled,
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
        geofenceMarkerOverlays.clear()
        geofenceMarkers.forEach { p -> p.remove() }
        geofenceMarkers.clear()
        polygons.forEach { p -> p.remove() }
        polygons.clear()
        if (locateMe) {
            areaList = Venue.getFilteredAreaList(currentFloor)
        } else {
            areaList = Venue.getFilteredAreaList(floorValue)
        }

        areaList.forEach { area ->
            area.areaProperty.geometry?.let { geometry ->
                val rectOptions = PolygonOptions().strokeColor(Color.TRANSPARENT)
                area.densityProperty?.densityColor?.let {color ->
                    rectOptions.fillColor(color.toInt()) } ?: kotlin.run {
                    rectOptions.fillColor(Color.BLUE)
                }
                geometry.forEach { point ->
                    rectOptions.add(LatLng(point.latitude, point.longitude))
                }
                val polygon = gmap.addPolygon(rectOptions)
                polygons.add(polygon)

                getPolygonCenterPoint(rectOptions)?.let { latLng ->
                    geofenceMarkerOverlays.add(
                        MarkerOptions().position(latLng)
                            .title(area.areaProperty.description)
                    )
                }
            }
        }
    }

    private fun setPois(iaVenue: IAVenue) {
        poiMarkers.forEach { p -> p.remove() }
        poiMarkers.clear()
        var f = floorValue
        if (locateMe) {
            f = currentFloor
        }
        iaVenue.poIs?.forEach { it ->
            if (it.floor == f) {
                val poiOverlay =
                    MarkerOptions().position(LatLng(it.location.latitude, it.location.longitude))
                        .title(it.name)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                poiMarkers.add(gmap.addMarker(poiOverlay))
            }
        }
    }

    private fun drawLabel() {
        if (currentFloorPlan == null && (isSameFloor || locateMe)) {
            appStatusViewModel.floorplanRegion.value?.let { fetchFloorPlanBitmap(it.floorPlan) }
        }
        geofenceMarkerOverlays.forEach {
            geofenceMarkers.add(gmap.addMarker(it))
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
        val iaLatLng = floorPlan.center
        val center = LatLng(iaLatLng.latitude, iaLatLng.longitude)
        val bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(bitmap)
        groundOverlay.image(bitmapDescriptor)
            .position(center, floorPlan.widthMeters, floorPlan.heightMeters)
            .bearing(floorPlan.bearing)
        mGroundOverlay = gmap.addGroundOverlay(groundOverlay)
    }

    private fun fetchFloorPlanBitmap(floorPlan: IAFloorPlan) {
        Picasso.get().load(floorPlan.url)
            .resize(2048, 0)
            .onlyScaleDown()
            .into(object : Target {
            override fun onBitmapLoaded(bitmap: Bitmap?, from: Picasso.LoadedFrom?) {
                bitmap?.let {
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

    override fun onEnterDensityRegion(region: IARegion) {
        logText.append(
            "${appStatusViewModel.getCurrentDateTime()} Enter density region \n" +
                    "name: ${region.name}\n" +
                    "region id: ${region.id} \n" +
                    "venue id: ${region.venue.id}\n\n"
        )
    }

    override fun onExitDensityRegion(region: IARegion) {
        Log.d("GmapFragment", "onExitDensityRegion")
        logText.append(
            "${appStatusViewModel.getCurrentDateTime()} Exit density region\n" +
                    "name: ${region.name} \n" +
                    "region id: ${region.id} \n" +
                    "venue id: ${region.venue.id}\n\n"
        )
    }

    override fun onUpdateDensity(venueDensity: IAGSVenueDensity?) {
        logText.append("${appStatusViewModel.getCurrentDateTime()} Update density\n")

        venueDensity?.area?.forEach {
            val item = Venue.areaList.first { row ->
                row.areaProperty.id == it.areaId
            }
            Venue.areaList.remove(item)
            Venue.areaList.add(RecyclerAdapter.AreaRow(item.areaProperty, it.densityProperty))
        }
        drawRegions()
        drawLabel()
    }

    override fun onEnterRegion(region: IARegion?) {
        Log.d("GmapFragment", "onEnterRegion")

        exitRegionText.visibility = View.GONE
        region?.let {
            if (it.type == IARegion.TYPE_FLOOR_PLAN) {
                logText.append("${appStatusViewModel.getCurrentDateTime()} Enter IA floorplan: ${it.name} \n")
                appStatusViewModel.floorplanRegion.postValue(it)
                currentFloor = it.floorPlan.floorLevel
                mVenue?.let { venue -> setPois(venue) }
                isSameFloor = it.floorPlan.floorLevel == floorValue
                val iaLatLng = it.floorPlan.center
                val center = LatLng(iaLatLng.latitude, iaLatLng.longitude)
                gmap.moveCamera(CameraUpdateFactory.newLatLngZoom(center, 22F))
                if (isSameFloor) {
                    fetchFloorPlanBitmap(it.floorPlan)
                }
                if (locateMe) {

                    logText.append("${appStatusViewModel.getCurrentDateTime()} update floorplan, geofences\n")
                    appStatusViewModel.selectedFloorLevel.postValue(currentFloor)
                    fetchFloorPlanBitmap(it.floorPlan)
                    drawRegions()
                    drawLabel()
                    if (it.floorPlan.floorLevel == 19) {
                        initDynamicSwitch(true)
                    } else {
                        initDynamicSwitch(false)
                    }
                }
            } else if (it.type == IARegion.TYPE_VENUE) {
                logText.append(
                    "${appStatusViewModel.getCurrentDateTime()} Enter IA region\n" +
                            "name: ${region.name} \n" +
                            "region id: ${region.id} \n" +
                            "venue id: ${region.venue.id}\n\n"
                )
                mVenue = it.venue
            }
        }
    }

    override fun onExitRegion(region: IARegion?) {
        //Clear blue dot and geofences color
        region?.let {
            if (it.type == IARegion.TYPE_VENUE) {
                logText.append(
                    "${appStatusViewModel.getCurrentDateTime()} Exit IA region\n" +
                            "name: ${region.name} \n" +
                            "region id: ${region.id} \n" +
                            "venue id: ${region.venue.id}\n\n"
                )
                exitRegionText.visibility = View.VISIBLE
                locationMarker?.remove()
                poiMarkers.forEach { p -> p.remove() }
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
                if (locateMe) {
                    mGroundOverlay?.remove()
                    currentFloorPlan = null
                }
            }
        }
    }

    override fun onLocationChanged(location: IALocation?) {
        if (currentFloorPlan == null && (isSameFloor || locateMe)) {
            appStatusViewModel.floorplanRegion.value?.let { fetchFloorPlanBitmap(it.floorPlan) }
        }
        location?.let {
            val point = LatLng(it.latitude, it.longitude)
            circleOverlay =
                CircleOptions().center(point).radius(it.accuracy.toDouble()).zIndex(99f)
                    .strokeWidth(3.0f)
            locationMarker?.remove()
            if (locateMe || it.floorLevel == floorValue) {
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
                if (locateMe){
                    setWayfindingTarget(it, true, currentFloor)
                } else {
                    setWayfindingTarget(it, true, floorValue)
                }
            }
        }
    }

    override fun onMarkerClick(marker: Marker?): Boolean {
        if (marker == wayfindingMarker) {
            mCurrentRoute = null
            mWayfindingDestination = null
            groundSageMgr.removeWayfindingUpdates()
            updateRouteVisualization()
            logText.append("${appStatusViewModel.getCurrentDateTime()} Wayfinding cancel\n")
            wayfindingMarker?.remove()
            wayfindingMarker = null
            return false
        }

        if (selectedPoiMarker == null) {
            //start wayfinding
            marker?.let {
                selectedPoiMarker = it
                if (locateMe){
                    setWayfindingTarget(marker.position, false, currentFloor)
                } else {
                    setWayfindingTarget(marker.position, false, floorValue)
                }
            }
        } else {
            //stop wayfinding
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
