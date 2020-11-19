package com.example.groundsage_example

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.*
import com.indooratlas.sdk.groundsage.IAGSManager
import com.indooratlas.sdk.groundsage.IAGSManagerListener
import com.indooratlas.sdk.groundsage.data.IAGSVenueDensity

class GMapActivity : AppCompatActivity(),
    IAGSManagerListener, OnMapReadyCallback {

    private lateinit var mapView: MapView
    private lateinit var gmap: GoogleMap
    private val polygons = mutableListOf<Polygon>()
    private val markers = mutableListOf<MarkerOptions>()
    private lateinit var areaList : List<RecyclerAdapter.AreaRow>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gmap)
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.let {
            Log.d("GMapActivity", "supportActionBar != null")
            it.setDisplayHomeAsUpEnabled(true)
        }
        mapView = findViewById(R.id.gmap)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

        val floorValue = intent.getIntExtra("floor", -1)
        areaList = Venue.getFilteredAreaList(floorValue)

        IAGSManager.getInstance(this).addGroundSageListener(this)
        Venue.getVenue()?.id?.let { IAGSManager.getInstance(this).startSubscription(it) }
    }

    override fun onMapReady(map: GoogleMap?) {
        Log.d("GMapActivity", "onMapReady")
        map?.let {
            gmap = map
            gmap.uiSettings.isMyLocationButtonEnabled = false
            gmap.uiSettings.isTiltGesturesEnabled = false
            gmap.isBuildingsEnabled = false
            gmap.setMinZoomPreference(14F)
            if (areaList.isNotEmpty()){
                areaList[0].areaProperty.geometry?.let {
                    val firstCoord = LatLng(it[0].latitude, it[0].longitude)
                    gmap.moveCamera(CameraUpdateFactory.newLatLng(firstCoord))
                }
            }
            drawRegions()
            drawLabel()
        }
    }

    private fun drawRegions() {
        gmap.clear()
        markers.clear()
        polygons.let {
            it.forEach { p -> p.remove() }
            it.clear()
        }

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
                            .title(areaList[i-1].areaProperty.description)
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

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        finish()
        return super.onSupportNavigateUp()
    }

    fun getPolygonCenterPoint(polygonPointsList: PolygonOptions): LatLng? {
        val builder: LatLngBounds.Builder = LatLngBounds.Builder()
        for (i in polygonPointsList.points) {
            builder.include(i)
        }
        val bounds: LatLngBounds = builder.build()
        return LatLng(bounds.center.latitude, bounds.center.longitude)
    }
}
