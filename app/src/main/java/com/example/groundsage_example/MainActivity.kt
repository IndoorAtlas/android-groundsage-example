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
import android.widget.FrameLayout
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
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
    lateinit var adapter: RecyclerAdapter

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
        lastUpdate = findViewById<TextView>(R.id.lastUpdate)
        appStatusViewModel.selectedFloorLevel.postValue(-999)
        appStatusViewModel.initWarningMessageStatus(this)
        appStatusViewModel.locationPermissionGranted.postValue(
            EasyPermissions.hasPermissions(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        )
        requestVenueInfo()

        val groundSageMgr = IAGSManager.getInstance(this)
        groundSageMgr.addGroundSageListener(this)
        groundSageMgr.addIARegionListener(this)
        subscriptionSwitch.setOnClickListener {
            if (subscriptionSwitch.isChecked) {
                groundSageMgr.startSubscription()
            } else {
                groundSageMgr.stopSubscription()
            }
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
        IAGSManager.getInstance(this).requestVenueInfo { venues, error ->
            if (venues != null && rows.size == 0) {
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
                    val areaList = mutableListOf<AreaRow>()
                    for (i in 1..it.size) {
                        rows.add(AreaRow(it[i - 1], null))
                        areaList.add(AreaRow(it[i - 1], null))
                    }
                    Venue.setAreaList(areaList)
                }
                adapter = RecyclerAdapter(rows, this)
                recyclerView.adapter = adapter
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
        venueDensity?.area?.let {
            for (i in 1..it.size) {
                val item = rows.first { row ->
                    row is AreaRow && row.areaProperty.id == it[i - 1].areaId
                }
                val area = item as AreaRow
                area.densityProperty = it[i - 1].densityProperty
            }
            Venue.clearAreaList()
            Venue.setAreaList(rows)
            adapter.notifyDataSetChanged()
        }
        IAGSManager.getInstance(this).extraInfo?.let {
            val traceId = it.traceId.substring(IntRange(0, it.traceId.indexOf(".") - 1))
            appStatusViewModel.traceID.postValue(String.format("Trace ID: $traceId"))
        }
    }

    override fun forwardClick(holder: FloorViewHolder) {
        Log.d("MainActivity", holder.floorLevel.text as String)
        appStatusViewModel.selectedFloorLevel.postValue(holder.floor)
        val fragment = GmapFragment()
        loadFragment(fragment)
    }

    fun loadFragment(fragment: Fragment) {
        val fragmentTransaction = supportFragmentManager.beginTransaction()
        fragmentTransaction.replace(R.id.frameLayout, fragment).commit()
    }

    override fun onEnterRegion(region: IARegion?) {
        Log.d("MainActivity", "onEnterRegion")
        if (region?.type == IARegion.TYPE_FLOOR_PLAN) {
            Log.d("MainActivity", "onEnterRegion save region")
            appStatusViewModel.region.postValue(region)
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

        Venue.clearAreaList()
        Venue.setAreaList(rows)
    }
}
