package com.clasli.hover_app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

class MapFragment : Fragment(), OnMapReadyCallback {
    private val LOCATION_REQUEST_CODE = 101
    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var locationRequest: LocationRequest
    private var currentLocation: LatLng? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        val view = inflater.inflate(R.layout.fragment_map, container, false)

        locationRequest = LocationRequest.create().apply {
            interval = 1000L // 10 seconds
            fastestInterval = 1000L // 5 seconds (the fastest interval for location updates)
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                locationResult?.locations?.let { locations ->
                    for (location in locations) {
                        // Handle location update
                        updateLocationOnMap(LatLng(location.latitude, location.longitude))
                    }
                }
            }
        }

        // Setting up buttons (if needed)
        val btn1 = view.findViewById<View>(R.id.btn1)
        val btn2 = view.findViewById<View>(R.id.btn2)
        btn1.setOnClickListener {
            val destLat = 37.7749
            val destLon = -122.4194
            locationBtnSelect(destLat, destLon)
        }
        btn2.setOnClickListener {
            val destLat = 34.01
            val destLon = -118.22
            locationBtnSelect(destLat, destLon)
        }

        return view
    }

    private fun locationBtnSelect(destLat: Double, destLon: Double){
        val intent = Intent(requireContext(), LocationService::class.java)
        intent.putExtra("DEST_LAT", destLat)
        intent.putExtra("DEST_LON", destLon)
        requireContext().startService(intent)
        val dest = LatLng(destLat, destLon)
        mMap.clear()
        mMap.addMarker(MarkerOptions().position(dest).title("Dest."))
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(dest, 15f))
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this) // Ensure the map is initialized properly

        val fragment = TerminalFragment()
        val deviceAddress = requireArguments().getString("device")
        val args = Bundle().apply {
            putString("device", deviceAddress) // Replace with actual Bluetooth device address
        }

        fragment.arguments = args
        childFragmentManager.beginTransaction()
            .replace(R.id.terminal_container, fragment)
            .commit()
    }

    private fun requestPermission(permissionType: String, requestCode: Int) {
        requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), requestCode)
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_REQUEST_CODE -> {
                if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(context, "Unable to show location - permission required", Toast.LENGTH_LONG).show()
                } else {
                    val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
                    mapFragment.getMapAsync(this) // Retry fetching the map after permission granted
                }
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        Log.d("MyMap", "map is ready")
        mMap = googleMap

        val permission = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)

        if (permission == PackageManager.PERMISSION_GRANTED) {
            mMap.isMyLocationEnabled = true

            // Get the last known location
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    Log.d("MyMap", "Location: $it")
                    val currentLatLng = LatLng(it.latitude, it.longitude)
//                    mMap.addMarker(MarkerOptions().position(currentLatLng).title("You are here"))
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
                } ?: run {
                    Toast.makeText(context, "Unable to get current location", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            requestPermission(Manifest.permission.ACCESS_FINE_LOCATION, LOCATION_REQUEST_CODE)
        }
    }

    private fun updateLocationOnMap(latLng: LatLng) {
        // Update the marker or camera position when location changes
        currentLocation = latLng
//        mMap.clear() // Clear previous markers
//        mMap.addMarker(MarkerOptions().position(latLng).title("You are here"))
//        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
        Log.d("MyMap", "newLoc = $latLng")
    }

    override fun onPause() {
        super.onPause()
        // Stop location updates when the fragment is paused
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    override fun onResume() {
        super.onResume()
        // Restart location updates when the fragment is resumed
        val permission = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
        if (permission == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
        }
    }

}
