package com.example.groundsage_example

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.EasyPermissions


class SplashActivity : AppCompatActivity(), EasyPermissions.PermissionCallbacks {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        requestLocationPermission()
    }

    @AfterPermissionGranted(PERMISSIONS_REQUEST_LOCATION)
    private fun requestLocationPermission() {
        val perms = Manifest.permission.ACCESS_FINE_LOCATION
        if (EasyPermissions.hasPermissions(this, perms)) {
            gotoMain()
        } else {
            EasyPermissions.requestPermissions(this, "Please grant the location permission", PERMISSIONS_REQUEST_LOCATION, perms)
        }
    }

    override fun onPermissionsGranted(requestCode: Int, perms: MutableList<String>) {
        gotoMain()
    }

    override fun onPermissionsDenied(requestCode: Int, perms: MutableList<String>) {
        gotoMain()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    companion object {
        const val PERMISSIONS_REQUEST_LOCATION = 99
    }

    private fun gotoMain() {
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }, 2000)

    }
}
