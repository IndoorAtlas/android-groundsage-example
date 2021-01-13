package com.example.groundsage_example

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.net.ConnectivityManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.FrameLayout
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import com.example.groundsage_example.RecyclerAdapter.*
import com.example.groundsage_example.databinding.ActivityMainBinding
import com.indooratlas.android.sdk.IARegion
import com.indooratlas.sdk.groundsage.IAGSManager
import com.indooratlas.sdk.groundsage.IAGSManagerListener
import com.indooratlas.sdk.groundsage.data.IAGSVenue
import com.indooratlas.sdk.groundsage.data.IAGSVenueDensity
import pub.devrel.easypermissions.EasyPermissions

class MainActivity : AppCompatActivity(), IAGSManagerListener, ClickEventHandler,
    IARegion.Listener {

    private lateinit var appStatusViewModel: AppStatusViewModel
    private lateinit var binding: ActivityMainBinding
    private var rows = mutableListOf<TableRow>()
    lateinit var recyclerView: RecyclerView
    private lateinit var frameLayout: FrameLayout
    private lateinit var title: TextView
    private lateinit var lastUpdate: TextView

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private lateinit var subscriptionSwitch: Switch
    private lateinit var locateMeButton: Button
    lateinit var adapter: RecyclerAdapter
    private lateinit var groundSageMgr:IAGSManager

    @SuppressLint("WrongConstant")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        appStatusViewModel = ViewModelProvider(this).get(AppStatusViewModel::class.java)
        binding = DataBindingUtil.setContentView<ActivityMainBinding>(this, R.layout.activity_main)
        binding.appStatusViewModel = appStatusViewModel
        binding.lifecycleOwner = this

        recyclerView = findViewById<RecyclerView>(R.id.densityListView)
        frameLayout = findViewById<FrameLayout>(R.id.frameLayout)
        title = findViewById(R.id.activityTitle)
        subscriptionSwitch = findViewById<Switch>(R.id.subscriptionSwitch)
        locateMeButton = findViewById<Button>(R.id.locateMeButton)
        lastUpdate = findViewById<TextView>(R.id.lastUpdate)
        appStatusViewModel.selectedFloorLevel.postValue(-999)
        appStatusViewModel.initWarningMessageStatus(this)
        appStatusViewModel.locationPermissionGranted.postValue(
            EasyPermissions.hasPermissions(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        )
        groundSageMgr = IAGSManager.getInstance(this)
        groundSageMgr.addGroundSageListener(this)
        groundSageMgr.addIARegionListener(this)
        appStatusViewModel.networkAvailable.observe(this, Observer {
            if (it){
                requestVenueInfo()
            }
        })
        subscriptionSwitch.setOnClickListener {
            if (subscriptionSwitch.isChecked) {
                groundSageMgr.startSubscription()
            } else {
                groundSageMgr.stopSubscription()
            }
        }
        locateMeButton.setOnClickListener {
            goToMapView(null)
        }
        startForegroundService()
    }

    private fun getForegroundServiceIntent(): Intent {
        return Intent(this, ForegroundService::class.java)
    }

    private fun startForegroundService() {
        ContextCompat.startForegroundService(this, getForegroundServiceIntent())
    }

    private fun stopForegroundService() {
        stopService(getForegroundServiceIntent())
    }

    private fun requestVenueInfo() {
        groundSageMgr.requestVenueInfo { venues, error ->
            if (venues != null && rows.size == 0) {
                Venue.setVenue(venues[0])
                val areaThreshold = String.format(
                    "Closed Area Threshold: %d",
                    venues[0].densityConfig.closedAreaThreshold
                )
                rows.add(HeaderRow(areaThreshold))
                rows.add(HeaderRow("Density"))
                venues[0].densityConfig.densityLevels?.forEach {
                    rows.add(DensityRow(it))
                }
                rows.add(HeaderRow("Floor"))
                venues[0].floors.forEach {
                    rows.add(FloorRow(it))
                }
                rows.add(HeaderRow("Area"))
                venues[0].areas.forEach {
                    rows.add(AreaRow(it, null))
                    Venue.areaList.add(AreaRow(it, null))
                }
                adapter = RecyclerAdapter(rows, this)
                recyclerView.adapter = adapter

                if (subscriptionSwitch.isChecked) {
                    groundSageMgr.startSubscription()
                }
            } else {
                Log.d("MainActivity", "venues is null")
            }
            if (error != null) {
                print("error %d \n" + error.message)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("MainActivity", "onResume")
        binding.appStatusViewModel?.networkChangeReceiver.let {
            val broadcastIntent = IntentFilter()
            broadcastIntent.addAction(ConnectivityManager.CONNECTIVITY_ACTION)
            broadcastIntent.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            broadcastIntent.addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
            registerReceiver(it, broadcastIntent)
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d("MainActivity", "onPause")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("MainActivity", "onDestroy")
        stopForegroundService()
        binding.appStatusViewModel?.networkChangeReceiver.let {
            unregisterReceiver(it)
        }
    }

    override fun onEnterDensityRegion(region: IARegion, venue: IAGSVenue) {
        Log.d("MainActivity", "onEnterDensityRegion")
    }

    override fun onExitDensityRegion(region: IARegion, venue: IAGSVenue) {
        Log.d("MainActivity", "onEnterDensityRegion")
    }

    override fun onUpdateDensity(venueDensity: IAGSVenueDensity?) {
        Log.d("MainActivity", "onUpdateDensity")
        //update density status
        appStatusViewModel.updateLastDensityUpdate()
        venueDensity?.area?.forEach {
            val rowItem = rows.first { row ->
                row is AreaRow && row.areaProperty.id == it.areaId
            }
            val areaRow = rowItem as AreaRow
            areaRow.densityProperty = it.densityProperty
        }
        adapter.notifyDataSetChanged()
        Venue.areaList.clear()
        Venue.setAreaList(rows)

        groundSageMgr.extraInfo?.let {
            val traceId = it.traceId.substring(IntRange(0, it.traceId.indexOf(".") - 1))
            appStatusViewModel.traceID.postValue(String.format("Trace ID: $traceId"))
        } ?: kotlin.run {
            appStatusViewModel.traceID.postValue(String.format("Trace ID: null"))
        }
    }

    override fun goToMapView(holder: FloorViewHolder?) {
        holder?.let {
            appStatusViewModel.selectedFloorLevel.postValue(holder.floor)
        } ?: kotlin.run {
            appStatusViewModel.selectedFloorLevel.postValue(null)
        }

        val fragment = GmapFragment()
        loadFragment(fragment)
    }

    private fun loadFragment(fragment: Fragment) {
        val fragmentTransaction = supportFragmentManager.beginTransaction()
        fragmentTransaction.replace(R.id.frameLayout, fragment).commit()
    }

    override fun onEnterRegion(region: IARegion?) {
        Log.d("MainActivity", "onEnterRegion")
        if (region?.type == IARegion.TYPE_FLOOR_PLAN) {
            Log.d("MainActivity", "onEnterRegion save floorplan")
            appStatusViewModel.floorplanRegion.postValue(region)
        } else if (region?.type == IARegion.TYPE_VENUE) {
            Log.d("MainActivity", "onEnterRegion save venue")
            appStatusViewModel.venueRegion.postValue(region)
        }
    }

    override fun onExitRegion(region: IARegion?) {
        //Clear all density properties

        rows.forEach {
            if (it is AreaRow) {
                it.densityProperty = null
            }
        }
        adapter.notifyDataSetChanged()

        Venue.areaList.clear()
        Venue.setAreaList(rows)
    }
}
