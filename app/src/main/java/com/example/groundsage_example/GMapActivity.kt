package com.example.groundsage_example

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Polygon
import com.google.android.gms.maps.model.PolygonOptions
import com.indooratlas.sdk.groundsage.IAGSManager
import com.indooratlas.sdk.groundsage.IAGSManagerListener
import com.indooratlas.sdk.groundsage.data.IAGSVenueDensity

class GMapActivity : AppCompatActivity(),
    IAGSManagerListener, OnMapReadyCallback {

    private lateinit var mapView: MapView
    private lateinit var gmap: GoogleMap
    val polygons = mutableListOf<Polygon>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gmap)

        mapView = findViewById(R.id.gmap)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

        IAGSManager.getInstance(this).addGroundSageListener(this)
        Venue.getVenue()?.id?.let { IAGSManager.getInstance(this).startSubscription(it) }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return super.onSupportNavigateUp()
    }

    override fun onMapReady(map: GoogleMap?) {
        Log.d("GMapActivity", "onMapReady")
        map?.let {
            gmap = map
            gmap.uiSettings.isMyLocationButtonEnabled = false
            gmap.uiSettings.isTiltGesturesEnabled = false
            gmap.isBuildingsEnabled = false
            gmap.setMinZoomPreference(19F)
            val location = LatLng(22.301292,114.173967)
            gmap.moveCamera(CameraUpdateFactory.newLatLng(location))
            drawRegions()
        }
    }

    private fun drawRegions(){

        polygons.let {
            it.forEach { p -> p.remove() }
            it.clear()
        }

        val floorValue = intent.getIntExtra("floor", -1)
        val areaList = Venue.getFilteredAreaList(floorValue)
        for (i in 1..areaList.size){
            areaList[i-1].areaProperty.geometry?.let {
                val rectOptions = PolygonOptions().fillColor(areaList[i-1].densityProperty?.densityColor?.toInt() ?: Color.BLUE).strokeColor(
                    Color.TRANSPARENT)
                for (j in 1..it.size){
                    rectOptions.add(LatLng(it[j-1].latitude, it[j-1].longitude))
                }
                val polygon = gmap.addPolygon(rectOptions)
                polygons.add(polygon)
            }
        }
    }

    override fun onResume() {
        mapView.onResume()
        super.onResume()
    }

    override fun onPause() {
        mapView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun didReceiveDensity(venueDensity: IAGSVenueDensity) {
        Log.d("GMapActivity", "didReceiveDensity")
        venueDensity.area?.let {
            for (i in 1 .. it.size){
                var item = Venue.getAreaList().first { row ->
                    row.areaProperty.id == it[i-1].id
                }
                var area = item as RecyclerAdapter.AreaRow
                area.densityProperty = it[i-1].densityProperty
            }
            drawRegions()
        }
    }
}