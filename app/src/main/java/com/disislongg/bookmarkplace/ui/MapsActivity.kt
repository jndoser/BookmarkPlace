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
    //Sử dụng lớp PlacesClient để sử dụng Place API,
    //nhằm lấy dữ liệu về các địa điểm có trên Google Maps
    private lateinit var placesClient: PlacesClient
    //Lớp này đại diện cho dữ liệu hiển thị trên maps
    //ở đây là các marker
    private lateinit var mapsViewModel: MapsViewModel
    //Adapter hỗ trợ hiển thị các địa điểm người dùng đã lưu lên
    //recyclerView của navigation bar
    private lateinit var bookmarkListAdapter: BookmarkListAdapter
    //HashMap hỗ trợ lưu trữ lại các marker đã được người dùng
    //lưu trữ
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


        //Nhận intent từ activity detail của từng bookmark đã lưu trữ,
        //nếu người dùng muốn chỉ đường đến địa điểm đã lưu thì ta nhận
        //vị trí điểm đến sau đó gọi hàm chỉ đường ,
        // nếu k nhận đc vị trí điểm đến thì bỏ qua
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
        //Set info window adapter để tuỳ biến lại nội dung hiển
        //thị của info window
        map.setInfoWindowAdapter(BookmarkInfoWindowAdapter(this))
        //Xử lý sự kiện khi người dùng click vào POI, tức là các địa
        //điểm hiển thị trên google maps
        //Khi người dùng click vào thì sẽ hiện info window lên
        map.setOnPoiClickListener {
            displayPoi(it)
        }
        //Xử lý sự kiện khi người dùng click vào Info window, khi người dùng
        //click vào info window thì sẽ lưu lại địa điểm đó vào sqlite db
        map.setOnInfoWindowClickListener { handleInfoWindowClick(it) }

        //Xử lý sự kiện khi người dùng chọn vào fab tìm kiếm , thực hiện
        //tìm kiếm và hiển thị địa điểm đó
        val fab = findViewById<FloatingActionButton>(R.id.fab)
        fab.setOnClickListener {
            searchAtCurrentLocation()
        }

        //Nếu ng dùng ấn giữ lâu vào một vị trí chưa có tên trên bản đồ
        //thì ta vẫn sẽ lưu lại địa điểm đó và cho người dùng tự điền thông
        //tin về địa điểm
        map.setOnMapLongClickListener{ latLng ->
            newBookmark(latLng)
        }
    }

    //Set up Place API để lấy vị trí các địa điểm
    //trên Google Maps
    private fun setupPlaceClient() {
        Places.initialize(applicationContext, "AIzaSyA-b9Bpa0V4NQxtyd1a_7K7PihnhJbQwC4")
        placesClient = Places.createClient(this)
    }

    //Set up lớp FusedLocationClient dùng để lấy vị trí
    //hiện tại của người dùng
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

    //Hàm hiển thị thông tin của POI bằng info window
    private fun displayPoi(pointOfInterest: PointOfInterest) {
        showProgress()
        displayPoiGetPlaceStep(pointOfInterest)

    }

    //Lấy thông tin từ POI nhờ placeAPI
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

    //Lấy hình ảnh của POI mà ng dùng chọn
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

    //Hiển thị marker POI và hiện window info của POI đó
    private fun displayPoiDisplayStep(place: Place, photo:Bitmap?) {
        hideProgress()
        val marker = map.addMarker(MarkerOptions()
            .position(place.latLng as LatLng)
            .title(place.name)
            .snippet(place.phoneNumber))

        marker?.tag = PlaceInfo(place, photo)
        marker?.showInfoWindow()
    }

    //Set up view model, view model là lớp trung gian giữa view , ở đây
    //là các marker có trên google maps và dữ liệu lưu trữ marker này trong
    //database
    private fun setupViewModel() {
        mapsViewModel = ViewModelProviders.of(this).get(MapsViewModel::class.java)
        createBookmarkObserver()
    }

    //Lớp này đại diện cho các địa điểm trên bản đồ mà
    //người dùng chưa lưu
    class PlaceInfo(val place: Place? = null,
    val image: Bitmap? = null)

    //Xử lý sự kiện khi người dùng chọn vào info window
    //nếu là info window của địa điểm chưa được lưu thì ta
    //sẽ lưu địa điêm đó, nếu là địa điểm đã lưu thì ta
    //chuyển sang activity chi tiết của địa điểm đó để người dùng
    //có thể xem chỉnh sửa
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

    //Hàm thêm marker vào bản đồ
    //đồng thời lưu nó vào hash map để
    //hiển thị lên , cho biết nó là địa điểm
    //mà ng dùng đã lưu
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

    //Hàm hiển thị tất cả các vị trí mà người dùng đã lưu
    //bằng marker đc định dạng riêng
    private fun displayAllBookmarks(
        bookmarks: List<MapsViewModel.BookmarkView>
    ) {
        for(bookmark in bookmarks) {
            addPlaceMarker(bookmark)
        }
    }

    //Hàm này sẽ cập nhật lại các marker khi
    //người dùng thêm mới, chỉnh sửa hoặc xoá
    //các bookmark đã lưu
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

    //Chuyển người dùng đến activity chi tiết của bookmark
    private fun startBookmarkDetails(bookmarkId: Long) {
        val intent = Intent(this, BookmarkDetailsActivity::class.java)
        intent.putExtra(EXTRA_BOOKMARK_ID, bookmarkId)
        startActivity(intent)
    }

    //Tạo toggle để hiện và tắt navigation bar bên phải màn hình,
    //navigation bar này sẽ là nơi lưu trữ các địa điểm đã lưu
    private fun setupToolbar() {
        val drawerLayout: DrawerLayout = findViewById(R.id.drawerLayout)
        val toolbar: androidx.appcompat.widget.Toolbar? = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        val toggle = ActionBarDrawerToggle(this, drawerLayout, toolbar,
        R.string.open_drawer, R.string.close_drawer)
        toggle.syncState()
    }

    //Sử dụng recyclerView để hiển thị các địa điểm đã lưu lên
    //navigation bar
    private fun setupNavigationDrawer() {
        val bookmarkRecycleView = findViewById<RecyclerView>(R.id.bookmarkRecyclerView)
        val layoutManager = LinearLayoutManager(this)
        bookmarkRecycleView.layoutManager = layoutManager
        bookmarkListAdapter = BookmarkListAdapter(null, this)
        bookmarkRecycleView.adapter = bookmarkListAdapter
    }

    //Hàm chuyển bản độ đến vị trí location
    private fun updateMapToLocation(location: Location) {
        val latLng = LatLng(location.latitude, location.longitude)
        map.animateCamera(
            CameraUpdateFactory.newLatLng(latLng)
        )
    }

    //Hàm chuyển bản đồ đến vị trí ng dùng đã bookmark
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

    //Hàm hỗ trợ tìm kiếm địa điểm trên google maps
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


    //Thêm menu để bật tắt chế độ ban đêm cho bản đồ
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.menu_main, menu)
        return true
    }

    //Xử lý sự kiện khi người dùng chọn thay đổi chủ đề của bản đồ
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

    //Hàm chỉ đường từ vị trí hiện tại đến vị trí mà ng dùng đã lưu
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