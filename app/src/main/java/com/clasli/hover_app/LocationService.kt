package com.clasli.hover_app

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.*
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*

class LocationService : Service(), SensorEventListener {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null
    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)

    private var newline = TextUtil.newline_crlf

    // TODO: needs to send same initial dest (b) as arduino to prevent state change)
    private var destLat: Double = 0.0
    private var destLon: Double = 0.0
    private var az: Float = 0.0F

    fun setDestination(lat: Double, lon: Double) {
        destLat = lat
        destLon = lon
    }

    override fun onCreate() {
        super.onCreate()

        // Location setup
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let {
                    Log.d("LocationService", "Lat: ${it.latitude}, Lng: ${it.longitude}")

                    // lat: 0.000, lon: 0.000, destLat: 0.0000, destLon: 0.0000, az: 0.0
                    val str = "lat: %.6f, lon: %.6f, destLat: %.6f, destLon: %.6f, az: %.1f".format(
                        it.latitude,
                        it.longitude,
                        destLat,
                        destLon,
                        az
                    )

                    val intent = Intent("com.clasli.SERIAL_SEND")
                    intent.putExtra("message", str)
                    sendBroadcast(intent)
                }
            }
        }

        // Sensor setup
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        startForeground(1, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            if (it.hasExtra("DEST_LAT") && it.hasExtra("DEST_LON")) {
                val lat = it.getDoubleExtra("DEST_LAT", 0.0)
                val lon = it.getDoubleExtra("DEST_LON", 0.0)
                setDestination(lat, lon)
                Log.d("LocationService", "Destination updated via intent: $lat, $lon")
            }
        }

        // Start location updates
        startLocationUpdates()

        // Start sensor updates
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        magnetometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        sensorManager.unregisterListener(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startLocationUpdates() {
        val request = LocationRequest.create().apply {
            interval = 1000
            fastestInterval = 1000
            priority = Priority.PRIORITY_HIGH_ACCURACY
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> System.arraycopy(event.values, 0, gravity, 0, 3)
            Sensor.TYPE_MAGNETIC_FIELD -> System.arraycopy(event.values, 0, geomagnetic, 0, 3)
        }

        val R = FloatArray(9)
        val I = FloatArray(9)
        if (SensorManager.getRotationMatrix(R, I, gravity, geomagnetic)) {
            val orientation = FloatArray(3)
            SensorManager.getOrientation(R, orientation)
            val azimuthDeg = Math.toDegrees(orientation[0].toDouble()).toFloat()
            val azimuth = ((azimuthDeg + 360) % 360)
            az = azimuth
//            Log.d("LocationService", "Azimuth: $azimuth°")
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun createNotification(): Notification {
        val channelId = "location_channel"
        val channelName = "Location Service"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Location & Orientation")
            .setContentText("Tracking position and direction")
            .setSmallIcon(R.drawable.ic_notification)
            .build()
    }
}