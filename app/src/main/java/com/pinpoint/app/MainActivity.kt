package com.pinpoint.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.liquidglass.LiquidGlassView
import com.google.android.gms.location.*
import kotlin.math.*

class MainActivity : AppCompatActivity() {

    // ─── ITT ÁLLÍTSD BE A CÉLPONTOT ───
    private val TARGET_LAT = 47.4979   // Példa: Budapest
    private val TARGET_LON = 19.0402
    private val UNLOCK_RADIUS = 30.0   // méter
    private val MAX_DISTANCE = 500.0

    private lateinit var glass: LiquidGlassView
    private lateinit var statusText: TextView
    private lateinit var distanceText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        glass = findViewById(R.id.glassCard)
        statusText = findViewById(R.id.statusText)
        distanceText = findViewById(R.id.distanceText)
        progressBar = findViewById(R.id.progressBar)

        glass.enableDynamicBackground = true

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        if (hasLocationPermission()) {
            startLocationUpdates()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                1001
            )
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates()
        } else {
            statusText.text = "Helymeghatározás engedély szükséges"
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
            .setMinUpdateIntervalMillis(1000L)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { updateUI(it) }
            }
        }

        fusedLocationClient.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper())
    }

    private fun updateUI(location: Location) {
        val distance = haversine(location.latitude, location.longitude, TARGET_LAT, TARGET_LON)

        if (distance <= UNLOCK_RADIUS) {
            statusText.text = "🎉 Kapszula kinyitva!"
            distanceText.text = "Titkos üzenet:\nMegtaláltad a Pinpoint kapszulát."
            progressBar.progress = 100
        } else {
            statusText.text = "Zárva – közelítsd meg a helyet"
            distanceText.text = "%.0f méterre vagy".format(distance)
            val progress = ((1.0 - (distance / MAX_DISTANCE).coerceIn(0.0, 1.0)) * 100).toInt()
            progressBar.progress = progress
        }
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    override fun onDestroy() {
        super.onDestroy()
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
    }
}
