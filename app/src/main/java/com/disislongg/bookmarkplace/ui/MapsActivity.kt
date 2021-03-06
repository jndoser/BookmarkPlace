package com.disislongg.bookmarkplace.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.renderscript.ScriptGroup
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.disislongg.bookmarkplace.R
import com.disislongg.bookmarkplace.adapter.BookmarkInfoWindowAdapter
import com.disislongg.bookmarkplace.adapter.BookmarkListAdapter
import com.disislongg.bookmarkplace.asynctask.DirectionAsync
import com.disislongg.bookmarkplace.databinding.ActivityMapsBinding
import com.disislongg.bookmarkplace.viewmodel.MapsViewModel
import com.google.android.gms.common.FirstPartyScopes
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.GooglePlayServicesRepairableException
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
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
import com.google.maps.android.PolyUtil
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.lang.Exception
import java.lang.StringBuilder
import java.net.HttpURLConnection
import java.net.URL
import java.util.ArrayList


class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityMapsBinding
    //S??? d???ng l???p PlacesClient ????? s??? d???ng Place API,
    //nh???m l???y d??? li???u v??? c??c ?????a ??i???m c?? tr??n Google Maps
    private lateinit var placesClient: PlacesClient
    //L???p n??y ?????i di???n cho d??? li???u hi???n th??? tr??n maps
    //??? ????y l?? c??c marker
    private lateinit var mapsViewModel: MapsViewModel
    //Adapter h??? tr??? hi???n th??? c??c ?????a ??i???m ng?????i d??ng ???? l??u l??n
    //recyclerView c???a navigation bar
    private lateinit var bookmarkListAdapter: BookmarkListAdapter
    //HashMap h??? tr??? l??u tr??? l???i c??c marker ???? ???????c ng?????i d??ng
    //l??u tr???
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


        //Nh???n intent t??? activity detail c???a t???ng bookmark ???? l??u tr???,
        //n???u ng?????i d??ng mu???n ch??? ???????ng ?????n ?????a ??i???m ???? l??u th?? ta nh???n
        //v??? tr?? ??i???m ?????n sau ???? g???i h??m ch??? ???????ng ,
        // n???u k nh???n ??c v??? tr?? ??i???m ?????n th?? b??? qua
        val intent = intent
        val dstLat = intent.getDoubleExtra("dstLat", 0.0)
        val dstLng = intent.getDoubleExtra("dstLng", 0.0)
        if(dstLat == 0.0 && dstLng == 0.0) {

        } else {
            val dst = LatLng(dstLat, dstLng)
            navigation(dst)
        }
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

        if(timesClick % 2 == 0) {
            val result = map.setMapStyle(MapStyleOptions(resources.getString(
                R.string.dark_theme_json
            )))
        }
    }

    private fun setupMapListeners() {
        //Set info window adapter ????? tu??? bi???n l???i n???i dung hi???n
        //th??? c???a info window
        map.setInfoWindowAdapter(BookmarkInfoWindowAdapter(this))
        //X??? l?? s??? ki???n khi ng?????i d??ng click v??o POI, t???c l?? c??c ?????a
        //??i???m hi???n th??? tr??n google maps
        //Khi ng?????i d??ng click v??o th?? s??? hi???n info window l??n
        map.setOnPoiClickListener {
            displayPoi(it)
        }
        //X??? l?? s??? ki???n khi ng?????i d??ng click v??o Info window, khi ng?????i d??ng
        //click v??o info window th?? s??? l??u l???i ?????a ??i???m ???? v??o sqlite db
        map.setOnInfoWindowClickListener { handleInfoWindowClick(it) }

        //X??? l?? s??? ki???n khi ng?????i d??ng ch???n v??o fab t??m ki???m , th???c hi???n
        //t??m ki???m v?? hi???n th??? ?????a ??i???m ????
        val fab = findViewById<FloatingActionButton>(R.id.fab)
        fab.setOnClickListener {
            searchAtCurrentLocation()
        }

        //N???u ng d??ng ???n gi??? l??u v??o m???t v??? tr?? ch??a c?? t??n tr??n b???n ?????
        //th?? ta v???n s??? l??u l???i ?????a ??i???m ???? v?? cho ng?????i d??ng t??? ??i???n th??ng
        //tin v??? ?????a ??i???m
        map.setOnMapLongClickListener{ latLng ->
            newBookmark(latLng)
        }
    }

    //Set up Place API ????? l???y v??? tr?? c??c ?????a ??i???m
    //tr??n Google Maps
    private fun setupPlaceClient() {
        Places.initialize(applicationContext, "AIzaSyA-b9Bpa0V4NQxtyd1a_7K7PihnhJbQwC4")
        placesClient = Places.createClient(this)
    }

    //Set up l???p FusedLocationClient d??ng ????? l???y v??? tr??
    //hi???n t???i c???a ng?????i d??ng
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
        lateinit var fusedLocationClient: FusedLocationProviderClient
        lateinit var map: GoogleMap
        var polyline_array:ArrayList<String> = ArrayList()
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

    //H??m hi???n th??? th??ng tin c???a POI b???ng info window
    private fun displayPoi(pointOfInterest: PointOfInterest) {
        showProgress()
        displayPoiGetPlaceStep(pointOfInterest)

    }

    //L???y th??ng tin t??? POI nh??? placeAPI
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

    //L???y h??nh ???nh c???a POI m?? ng d??ng ch???n
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

    //Hi???n th??? marker POI v?? hi???n window info c???a POI ????
    private fun displayPoiDisplayStep(place: Place, photo:Bitmap?) {
        hideProgress()
        val marker = map.addMarker(MarkerOptions()
            .position(place.latLng as LatLng)
            .title(place.name)
            .snippet(place.phoneNumber))

        marker?.tag = PlaceInfo(place, photo)
        marker?.showInfoWindow()
    }

    //Set up view model, view model l?? l???p trung gian gi???a view , ??? ????y
    //l?? c??c marker c?? tr??n google maps v?? d??? li???u l??u tr??? marker n??y trong
    //database
    private fun setupViewModel() {
        mapsViewModel = ViewModelProviders.of(this).get(MapsViewModel::class.java)
        createBookmarkObserver()
    }

    //L???p n??y ?????i di???n cho c??c ?????a ??i???m tr??n b???n ????? m??
    //ng?????i d??ng ch??a l??u
    class PlaceInfo(val place: Place? = null,
    val image: Bitmap? = null)

    //X??? l?? s??? ki???n khi ng?????i d??ng ch???n v??o info window
    //n???u l?? info window c???a ?????a ??i???m ch??a ???????c l??u th?? ta
    //s??? l??u ?????a ??i??m ????, n???u l?? ?????a ??i???m ???? l??u th?? ta
    //chuy???n sang activity chi ti???t c???a ?????a ??i???m ???? ????? ng?????i d??ng
    //c?? th??? xem ch???nh s???a
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

    //H??m th??m marker v??o b???n ?????
    //?????ng th???i l??u n?? v??o hash map ?????
    //hi???n th??? l??n , cho bi???t n?? l?? ?????a ??i???m
    //m?? ng d??ng ???? l??u
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

    //H??m hi???n th??? t???t c??? c??c v??? tr?? m?? ng?????i d??ng ???? l??u
    //b???ng marker ??c ?????nh d???ng ri??ng
    private fun displayAllBookmarks(
        bookmarks: List<MapsViewModel.BookmarkView>
    ) {
        for(bookmark in bookmarks) {
            addPlaceMarker(bookmark)
        }
    }

    //H??m n??y s??? c???p nh???t l???i c??c marker khi
    //ng?????i d??ng th??m m???i, ch???nh s???a ho???c xo??
    //c??c bookmark ???? l??u
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

    //Chuy???n ng?????i d??ng ?????n activity chi ti???t c???a bookmark
    private fun startBookmarkDetails(bookmarkId: Long) {
        val intent = Intent(this, BookmarkDetailsActivity::class.java)
        intent.putExtra(EXTRA_BOOKMARK_ID, bookmarkId)
        startActivity(intent)
    }

    //T???o toggle ????? hi???n v?? t???t navigation bar b??n ph???i m??n h??nh,
    //navigation bar n??y s??? l?? n??i l??u tr??? c??c ?????a ??i???m ???? l??u
    private fun setupToolbar() {
        val drawerLayout: DrawerLayout = findViewById(R.id.drawerLayout)
        val toolbar: androidx.appcompat.widget.Toolbar? = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        val toggle = ActionBarDrawerToggle(this, drawerLayout, toolbar,
        R.string.open_drawer, R.string.close_drawer)
        toggle.syncState()
    }

    //S??? d???ng recyclerView ????? hi???n th??? c??c ?????a ??i???m ???? l??u l??n
    //navigation bar
    private fun setupNavigationDrawer() {
        val bookmarkRecycleView = findViewById<RecyclerView>(R.id.bookmarkRecyclerView)
        val layoutManager = LinearLayoutManager(this)
        bookmarkRecycleView.layoutManager = layoutManager
        bookmarkListAdapter = BookmarkListAdapter(null, this)
        bookmarkRecycleView.adapter = bookmarkListAdapter
    }

    //H??m chuy???n b???n ????? ?????n v??? tr?? location
    private fun updateMapToLocation(location: Location) {
        val latLng = LatLng(location.latitude, location.longitude)
        map.animateCamera(
            CameraUpdateFactory.newLatLng(latLng)
        )
    }

    //H??m chuy???n b???n ????? ?????n v??? tr?? ng d??ng ???? bookmark
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

    //H??m h??? tr??? t??m ki???m ?????a ??i???m tr??n google maps
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


    //Th??m menu ????? b???t t???t ch??? ????? ban ????m cho b???n ?????
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.menu_main, menu)
        return true
    }

    //X??? l?? s??? ki???n khi ng?????i d??ng ch???n thay ?????i ch??? ????? c???a b???n ?????
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

    //H??m ch??? ???????ng t??? v??? tr?? hi???n t???i ?????n v??? tr?? m?? ng d??ng ???? l??u
    fun navigation(dstLatLng: LatLng) {
        val url = "https://maps.googleapis.com" +
                "/maps/api/directions/json?" +
                "origin=14.441619505710758,%20109.01459628236806" +
                "&destination=14.444005498346867,%20109.01816278623009" +
                "&key=AIzaSyA-b9Bpa0V4NQxtyd1a_7K7PihnhJbQwC4"

     if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {

        }
        fusedLocationClient.lastLocation.addOnSuccessListener {
            val urls = "https://maps.googleapis.com/maps/api/directions/json?" +
                    "origin=${it.latitude},${it.longitude}" +
                    "&destination=${dstLatLng.latitude},${dstLatLng.longitude}" +
                    "&key=AIzaSyA-b9Bpa0V4NQxtyd1a_7K7PihnhJbQwC4"
            val async = DirectionAsync()
            async.execute(urls)
        }
        }

}