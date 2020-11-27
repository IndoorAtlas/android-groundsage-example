package com.example.groundsage_example

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.example.groundsage_example.RecyclerAdapter.*
import com.indooratlas.sdk.groundsage.IAGSManager
import com.indooratlas.sdk.groundsage.IAGSManagerListener
import com.indooratlas.sdk.groundsage.data.IAGSVenueDensity
import pub.devrel.easypermissions.EasyPermissions
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity(), IAGSManagerListener, ClickEventHandler {

    private var rows = mutableListOf<TableRow>()
    lateinit var recyclerView: RecyclerView
    private lateinit var mainLayout: ConstraintLayout
    private lateinit var lastUpdate:TextView
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private lateinit var subscriptionSwitch: Switch
    lateinit var adapter: RecyclerAdapter

    @SuppressLint("WrongConstant")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        recyclerView = findViewById<RecyclerView>(R.id.densityListView)
        subscriptionSwitch = findViewById<Switch>(R.id.subscriptionSwitch)
        lastUpdate = findViewById<TextView>(R.id.lastUpdate)
        mainLayout = findViewById(R.id.mainLayout)
        IAGSManager.getInstance(this).requestVenueInfo { venues, error ->
            if (venues != null) {
                Venue.setVenue(venues[0])
                val areaThreshold = String.format(
                    "Closed Area Threshold: %d",
                    venues[0].densityConfig.closedAreaThreshold
                )
                rows.add(HeaderRow(areaThreshold))
                rows.add(HeaderRow("Density"))
                venues[0].densityConfig.densityLevels?.let {
                    for (i in 1..it.size) {
                        rows.add(DensityRow(it[i - 1]))
                    }
                }
                rows.add(HeaderRow("Floor"))
                venues[0].floors.let {
                    for (i in 1..it.size) {
                        rows.add(FloorRow(it[i - 1]))
                    }
                }
                rows.add(HeaderRow("Area"))
                venues[0].areas.let {
                    val areaList = mutableListOf<RecyclerAdapter.AreaRow>()
                    for (i in 1..it.size) {
                        rows.add(AreaRow(it[i - 1], null))
                        areaList.add(AreaRow(it[i - 1], null))
                    }
                    Venue.setAreaList(areaList)
                }
                adapter = RecyclerAdapter(rows, this)
                recyclerView.adapter = adapter


                if (EasyPermissions.hasPermissions(this, Manifest.permission.ACCESS_FINE_LOCATION)){
                    subscriptionSwitch.isEnabled = true
                    val locationManager = IAGSManager.getInstance(this)

                }
            }
            if (error != null) {
                print("error %d \n" + error.message)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("MainActivity", "onResume")
        val groundSageMgr = IAGSManager.getInstance(this)
        groundSageMgr.requestVenueInfo { _, _ ->
            Log.d("MainActivity", "onResume requestVenueInfo")
            groundSageMgr.addGroundSageListener(this)
            subscriptionSwitch.setOnClickListener {
                if (subscriptionSwitch.isChecked) {
                    Log.d("MainActivity", "onResume subscriptionSwitch is Checked")
                    groundSageMgr.startSubscription()
                } else {
                    Log.d("MainActivity", "onResume subscriptionSwitch is not Checked")
                    groundSageMgr.stopSubscription()
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d("MainActivity", "onPause")
        if (subscriptionSwitch.isChecked) {
            IAGSManager.getInstance(this).stopSubscription()
            IAGSManager.getInstance(this).removeGroundSageListener(this)
            subscriptionSwitch.isChecked = false
        }
    }

    override fun didReceiveDensity(venueDensity: IAGSVenueDensity) {
        Log.d("MainActivity", "didReceiveDensity")
        //update density status
        venueDensity.area?.let {
            for (i in 1..it.size) {
                val item = rows.first { row ->
                    row is AreaRow && row.areaProperty.id == it[i - 1].id
                }
                val area = item as AreaRow
                area.densityProperty = it[i - 1].densityProperty
            }
            Venue.clearAreaList()
            Venue.setAreaList(rows)
            adapter.notifyDataSetChanged()
            val currentDateTime = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()).format(Date())
            lastUpdate.text = String.format("Last density update: $currentDateTime")
        }
    }

    override fun forwardClick(holder: FloorViewHolder) {
        Log.d("MainActivity", holder.floorLevel.text as String)
        val intent = Intent(this, GMapActivity::class.java)
        intent.putExtra("floor", holder.floor)
        startActivity(intent)
    }
}
