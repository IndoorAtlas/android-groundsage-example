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
import androidx.constraintlayout.widget.ConstraintLayout
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
import com.indooratlas.sdk.groundsage.data.IAGSVenueDensity
import pub.devrel.easypermissions.EasyPermissions

class MainActivity : AppCompatActivity(), IAGSManagerListener, ClickEventHandler,
    IARegion.Listener {

    private lateinit var networkViewModel: NetworkViewModel
    private lateinit var binding: ActivityMainBinding
    private var rows = mutableListOf<TableRow>()
    lateinit var recyclerView: RecyclerView
    private lateinit var frameLayout: FrameLayout
    private lateinit var mainLayout: ConstraintLayout
    private lateinit var title: TextView
    private lateinit var lastUpdate: TextView

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private lateinit var subscriptionSwitch: Switch
    lateinit var adapter: RecyclerAdapter

    @SuppressLint("WrongConstant")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        networkViewModel = ViewModelProvider(this).get(NetworkViewModel::class.java)
        binding = DataBindingUtil.setContentView<ActivityMainBinding>(this, R.layout.activity_main)
        binding.networkViewModel = networkViewModel
        binding.lifecycleOwner = this

        recyclerView = findViewById<RecyclerView>(R.id.densityListView)
        frameLayout = findViewById<FrameLayout>(R.id.frameLayout)
        title = findViewById(R.id.activityTitle)
        subscriptionSwitch = findViewById<Switch>(R.id.subscriptionSwitch)
        lastUpdate = findViewById<TextView>(R.id.lastUpdate)
        mainLayout = findViewById(R.id.mainLayout)
        networkViewModel.selectedFloorLevel.postValue(-999)
        networkViewModel.networkLiveData.observe(this, androidx.lifecycle.Observer { value ->
            if (value) {
                requestVenueInfo()
            }
        })
        networkViewModel.initWarningMessageStatus(this)
        networkViewModel.locationPermissionGranted.postValue(
            EasyPermissions.hasPermissions(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        )
        startForegroundService()
    }

    private fun getForegroundServiceIntent(): Intent {
        return Intent(this, ForegroundService::class.java)
    }

    private fun startForegroundService(){
        ContextCompat.startForegroundService(this, getForegroundServiceIntent())
    }

    private fun requestVenueInfo(){
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
                subscriptionSwitch.isEnabled =
                    EasyPermissions.hasPermissions(this, Manifest.permission.ACCESS_FINE_LOCATION)
            }
            if (error != null) {
                print("error %d \n" + error.message)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("MainActivity", "onResume")
        binding.networkViewModel?.networkChangeReceiver.let {
            val broadcastIntent = IntentFilter()
            broadcastIntent.addAction(ConnectivityManager.CONNECTIVITY_ACTION)
            broadcastIntent.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            broadcastIntent.addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
            registerReceiver(it, broadcastIntent)
        }
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
    }

    override fun onPause() {
        super.onPause()
        Log.d("MainActivity", "onPause")
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.networkViewModel?.networkChangeReceiver.let {
            unregisterReceiver(it)
        }
    }

    override fun didReceiveDensity(venueDensity: IAGSVenueDensity) {
        Log.d("MainActivity", "didReceiveDensity")
        //update density status
        networkViewModel.updateLastDensityUpdate()
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
        }
    }

    override fun forwardClick(holder: FloorViewHolder) {
        Log.d("MainActivity", holder.floorLevel.text as String)
        networkViewModel.selectedFloorLevel.postValue(holder.floor)
        val fragment = GmapFragment()
        loadFragment(fragment)
    }

    fun loadFragment(fragment: Fragment){
        val fragmentTransaction = supportFragmentManager.beginTransaction()
        fragmentTransaction.replace(R.id.frameLayout, fragment).commit()
    }

    override fun onEnterRegion(region: IARegion?) {
        Log.d("MainActivity", "onEnterRegion")
        if (region?.type == IARegion.TYPE_FLOOR_PLAN){
            Log.d("MainActivity", "onEnterRegion save region")
            networkViewModel.region.postValue(region)
        }
    }

    override fun onExitRegion(region: IARegion?) {
        //Clear all density properties

        rows.forEach {
            if (it is AreaRow){
                it.densityProperty = null
            }
        }
        adapter.notifyDataSetChanged()

        Venue.clearAreaList()
        Venue.setAreaList(rows)
    }
}
