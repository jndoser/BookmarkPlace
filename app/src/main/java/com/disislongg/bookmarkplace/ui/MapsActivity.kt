package com.disislongg.bookmarkplace.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.location.Location
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.core.app.ActivityCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.disislongg.bookmarkplace.R
import com.disislongg.bookmarkplace.adapter.BookmarkInfoWindowAdapter
import com.disislongg.bookmarkplace.adapter.BookmarkListAdapter

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.disislongg.bookmarkplace.databinding.ActivityMapsBinding
import com.disislongg.bookmarkplace.viewmodel.MapsViewModel
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.GooglePlayServicesRepairableException
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.PhotoMetadata
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.api.net.FetchPhotoRequest
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus


class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var binding: ActivityMapsBinding
    private lateinit var placesClient: PlacesClient
    private lateinit var mapsViewModel: MapsViewModel
    private lateinit var bookmarkListAdapter: BookmarkListAdapter
    private var markers = HashMap<Long, Marker>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        setupToolbar()
        setupLocationClient()
        setupPlaceClient()
        setupNavigationDrawer()
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap

        setupMapListeners()

        setupViewModel()
        getCurrentLocation()

    }

    private fun setupMapListeners() {
        map.setInfoWindowAdapter(BookmarkInfoWindowAdapter(this))
        map.setOnPoiClickListener {
            displayPoi(it)
        }
        map.setOnInfoWindowClickListener { handleInfoWindowClick(it) }
        val fab = findViewById<FloatingActionButton>(R.id.fab)
        fab.setOnClickListener {
            searchAtCurrentLocation()
        }
        map.setOnMapLongClickListener{ latLng ->
            newBookmark(latLng)
        }
    }

    private fun setupPlaceClient() {
        Places.initialize(applicationContext, "AIzaSyAk8hMWLwJRN3pXDztLS9ppO48E0YQZLjA")
        placesClient = Places.createClient(this)
    }

    private fun setupLocationClient() {
        fusedLocationClient =
            LocationServices.getFusedLocationProviderClient(this)
    }

    private fun requestLocationPermissions() {
        ActivityCompat.requestPermissions(this,
        arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
        REQUEST_LOCATION
        )
    }

    companion object {
        const val EXTRA_BOOKMARK_ID = "com.disislongg.bookmarkplace.EXTRA_BOOKMARK_ID"
        private const val REQUEST_LOCATION = 1
        private const val TAG = "MapsActivity"
        private const val AUTOCOMPLETE_REQUEST_CODE = 2
        private var timesClick = 1
    }

    private fun getCurrentLocation() {
        if(ActivityCompat.checkSelfPermission(this,
            android.Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED) {
            requestLocationPermissions()
        } else {
            map.isMyLocationEnabled = true
            fusedLocationClient.lastLocation.addOnCompleteListener {
                val location = it.result
                if(location != null) {
                    val latLng = LatLng(location.latitude, location.longitude)
                    val update = CameraUpdateFactory.newLatLngZoom(latLng, 16.0f)
                    map.moveCamera(update)
                } else {
                    Log.e(TAG, "No location found")
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(requestCode == REQUEST_LOCATION) {
            if(grantResults.size == 1 && grantResults[0] ==
                    PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation()
            } else {
                Log.e(TAG, "Location permission denied")
            }
        }
    }

    private fun displayPoi(pointOfInterest: PointOfInterest) {
        showProgress()
        displayPoiGetPlaceStep(pointOfInterest)

    }

    private fun displayPoiGetPlaceStep(pointOfInterest: PointOfInterest) {
        val placeId = pointOfInterest.placeId

        val placeFields = listOf(
            Place.Field.ID,
            Place.Field.NAME,
            Place.Field.PHONE_NUMBER,
            Place.Field.PHOTO_METADATAS, Place.Field.ADDRESS,
            Place.Field.LAT_LNG
        )

        val request = FetchPlaceRequest.builder(placeId, placeFields)
            .build()

        placesClient.fetchPlace(request)
            .addOnSuccessListener { response ->
                val place = response.place
                displayPoiGetPhotoStep(place)
            }.addOnFailureListener { exception ->
                if (exception is ApiException) {
                    val statusCode = exception.statusCode
                    Log.e(
                        TAG, "Place not found: " +
                                "${exception.message} , statusCode: $statusCode"
                    )
                    hideProgress()
                }
            }
    }

    private fun displayPoiGetPhotoStep(place: Place) {
        val photoMetadata = place.photoMetadatas?.get(0)

        if(photoMetadata == null) {
            displayPoiDisplayStep(place, null)
            return
        }

        val photoRequest = FetchPhotoRequest
            .builder(photoMetadata as PhotoMetadata)
            .setMaxWidth(resources.getDimensionPixelSize(
                R.dimen.default_image_width
            ))
            .setMaxHeight(resources.getDimensionPixelSize(
                R.dimen.default_image_height
            )).build()

        placesClient.fetchPhoto(photoRequest)
            .addOnSuccessListener { fetchPhotoResponse ->
                val bitmap = fetchPhotoResponse.bitmap
                displayPoiDisplayStep(place, bitmap)
            }.addOnFailureListener { exception ->
                if(exception is ApiException) {
                    val statusCode = exception.statusCode
                    Log.e(
                        TAG, "Place not found: " +
                            "${exception.message} , " +
                            "statusCode: $statusCode")
                    hideProgress()
                }
            }
    }

    private fun displayPoiDisplayStep(place: Place, photo:Bitmap?) {
        hideProgress()
        val marker = map.addMarker(MarkerOptions()
            .position(place.latLng as LatLng)
            .title(place.name)
            .snippet(place.phoneNumber))

        marker?.tag = PlaceInfo(place, photo)
        marker?.showInfoWindow()
    }

    private fun setupViewModel() {
        mapsViewModel = ViewModelProviders.of(this).get(MapsViewModel::class.java)
        createBookmarkObserver()
    }

    class PlaceInfo(val place: Place? = null,
    val image: Bitmap? = null)

    private fun handleInfoWindowClick(marker: Marker) {
        when(marker.tag) {
            is MapsActivity.PlaceInfo -> {
                val placeInfo = marker.tag as PlaceInfo
                if(placeInfo.place != null) {
                    GlobalScope.launch {
                        mapsViewModel.addBookmarkFromPlace(placeInfo.place,
                            placeInfo.image)
                    }
                }
                marker.remove()
            }
            is MapsViewModel.BookmarkView -> {
                val bookmarkMarkerView = (marker.tag
                        as MapsViewModel.BookmarkView)
                marker.hideInfoWindow()
                bookmarkMarkerView.id?.let {
                    startBookmarkDetails(it)
                }
            }
        }

    }

    private fun addPlaceMarker(
        bookmark: MapsViewModel.BookmarkView
    ): Marker? {
        val marker = map.addMarker(MarkerOptions()
            .position(bookmark.location)
            .title(bookmark.name)
            .snippet(bookmark.phone)
            .icon(bookmark.categoryResourceId?.let {
                BitmapDescriptorFactory.fromResource(it)
            }).alpha(0.8f))

        marker.tag = bookmark
        bookmark.id?.let { markers.put(it, marker) }

        return marker
    }

    private fun displayAllBookmarks(
        bookmarks: List<MapsViewModel.BookmarkView>
    ) {
        for(bookmark in bookmarks) {
            addPlaceMarker(bookmark)
        }
    }

    private fun createBookmarkObserver() {
        mapsViewModel.getBookmarkViews()?.observe(
            this,
            androidx.lifecycle.Observer<List<MapsViewModel.BookmarkView>> {
                map.clear()
                markers.clear()
                it?.let {
                    displayAllBookmarks(it)
                    bookmarkListAdapter.setBookmarkData(it)
                }
            }
        )
    }

    private fun startBookmarkDetails(bookmarkId: Long) {
        val intent = Intent(this, BookmarkDetailsActivity::class.java)
        intent.putExtra(EXTRA_BOOKMARK_ID, bookmarkId)
        startActivity(intent)
    }

    private fun setupToolbar() {
        val drawerLayout: DrawerLayout = findViewById(R.id.drawerLayout)
        val toolbar: androidx.appcompat.widget.Toolbar? = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        val toggle = ActionBarDrawerToggle(this, drawerLayout, toolbar,
        R.string.open_drawer, R.string.close_drawer)
        toggle.syncState()
    }

    private fun setupNavigationDrawer() {
        val bookmarkRecycleView = findViewById<RecyclerView>(R.id.bookmarkRecyclerView)
        val layoutManager = LinearLayoutManager(this)
        bookmarkRecycleView.layoutManager = layoutManager
        bookmarkListAdapter = BookmarkListAdapter(null, this)
        bookmarkRecycleView.adapter = bookmarkListAdapter
    }

    private fun updateMapToLocation(location: Location) {
        val latLng = LatLng(location.latitude, location.longitude)
        map.animateCamera(
            CameraUpdateFactory.newLatLng(latLng)
        )
    }

    fun moveToBookmark(bookmark: MapsViewModel.BookmarkView) {
        val drawerLayout = findViewById<DrawerLayout>(R.id.drawerLayout)
        drawerLayout.closeDrawer(GravityCompat.START)
        val marker = markers[bookmark.id]
        marker?.showInfoWindow()

        val location = Location("")
        location.latitude = bookmark.location.latitude
        location.longitude = bookmark.location.longitude
        updateMapToLocation(location)
    }

    private fun searchAtCurrentLocation() {
        val placeFields = listOf(
            Place.Field.ID,
            Place.Field.NAME,
            Place.Field.PHONE_NUMBER,
            Place.Field.PHOTO_METADATAS,
            Place.Field.LAT_LNG,
            Place.Field.ADDRESS,
            Place.Field.TYPES
        )

        val bounds =
            RectangularBounds.newInstance(map.projection.visibleRegion.latLngBounds)
        try {
            val intent = Autocomplete.IntentBuilder(
                AutocompleteActivityMode.OVERLAY, placeFields
            ).setLocationBias(bounds).build(this)

            startActivityForResult(intent, AUTOCOMPLETE_REQUEST_CODE)
        } catch (e: GooglePlayServicesRepairableException) {

        } catch (e: GooglePlayServicesNotAvailableException) {

        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {

        when(requestCode) {
            AUTOCOMPLETE_REQUEST_CODE ->
                if(resultCode == RESULT_OK && data != null) {
                    val place = Autocomplete.getPlaceFromIntent(data)
                    val location = Location("")
                    location.latitude = place.latLng?.latitude ?: 0.0
                    location.longitude = place.latLng?.longitude ?: 0.0

                    showProgress()
                    updateMapToLocation(location)
                    displayPoiGetPhotoStep(place)
                }
            else {
                    super.onActivityResult(requestCode, resultCode, data)
                }
        }
    }

    private fun newBookmark(latLng: LatLng) {
        GlobalScope.launch {
            val bookmarkId = mapsViewModel.addBookmark(latLng)
            bookmarkId?.let { startBookmarkDetails(it) }
        }
    }

    private fun disableUserInteraction() {
        window.setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
    }

    private fun enableUserInteraction() {
        window.clearFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        )
    }

    private fun showProgress() {
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        progressBar.visibility = ProgressBar.VISIBLE
        disableUserInteraction()
    }

    private fun hideProgress() {
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        progressBar.visibility = ProgressBar.GONE
        enableUserInteraction()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId) {
            R.id.status_darktheme -> {
                timesClick++
                Log.e("VALUE TIMES CLICK", timesClick.toString())
                if(timesClick % 2 == 0) {
                    val result = map.setMapStyle(MapStyleOptions(resources.getString(
                        R.string.dark_theme_json
                    )))
                    return true
                } else {
                    val result = map.setMapStyle(
                        MapStyleOptions(
                            resources.getString(
                                R.string.default_theme_json
                            )
                        )
                    )
                    return true
                }
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }
}