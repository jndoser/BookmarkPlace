package com.disislongg.bookmarkplace.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.Transformations
import com.disislongg.bookmarkplace.model.Bookmark
import com.disislongg.bookmarkplace.repository.BookmarkRepo
import com.disislongg.bookmarkplace.util.ImageUtils
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.Place

class MapsViewModel(application: Application):
        AndroidViewModel(application) {
            private val TAG = "MapsViewModel"

            private var bookmarkRepo: BookmarkRepo = BookmarkRepo(getApplication())
            private var bookmarks: LiveData<List<BookmarkView>>? = null

            fun addBookmarkFromPlace(place: Place, image: Bitmap?) {
                val bookmark = bookmarkRepo.createBookmark()
                bookmark.placeId = place.id
                bookmark.name = place.name
                bookmark.longitude = place.latLng?.longitude ?: 0.0
                bookmark.latitude = place.latLng?.latitude ?: 0.0
                bookmark.phone = place.phoneNumber.toString()
                bookmark.address = place.address.toString()
                bookmark.category = getPlaceCategory(place)

                val newId = bookmarkRepo.addBookmark(bookmark)
                image?.let { bookmark.setImage(it, getApplication()) }

                Log.i(TAG, "New bookmark $newId added to the database.")
            }

        data class BookmarkView(
            val id: Long? = null,
            var location: LatLng = LatLng(0.0, 0.0),
            var name: String? = "",
            var phone: String = "",
            val categoryResourceId: Int? = null
        ) {
            fun getImage(context: Context): Bitmap? {
                id?.let {
                    return ImageUtils.loadBitmapFromFile(context,
                    Bookmark.generateImageFilename(it))
                }
                return null
            }
        }

        private fun bookmarkToBookmarkView(bookmark: Bookmark): BookmarkView {
            return BookmarkView(bookmark.id,
            LatLng(bookmark.latitude, bookmark.longitude),
                bookmark.name,
                bookmark.phone,
                bookmarkRepo.getCategoryResourceId(bookmark.category)
            )
        }

        private fun mapBookmarksToBookmarkView() {
            bookmarks = Transformations.map(bookmarkRepo.allBookmarks) {
                repoBookmarks ->
                repoBookmarks.map { bookmark ->
                    bookmarkToBookmarkView(bookmark)
                }
            }
        }

        fun getBookmarkViews(): LiveData<List<BookmarkView>>? {
            if(bookmarks == null) {
                mapBookmarksToBookmarkView()
            }
            return bookmarks
        }

        private fun getPlaceCategory(place: Place): String {
            var category = "Other"
            val placeTypes = place.types
            if (placeTypes != null) {
                if(placeTypes.size > 0) {
                    val placeType = placeTypes[0].ordinal
                    category = bookmarkRepo.placeTypeToCategory(placeType)
                }
            }
            return category
        }

        fun addBookmark(latLng: LatLng): Long? {
            val bookmark = bookmarkRepo.createBookmark()

            bookmark.name = "Untitled"
            bookmark.longitude = latLng.longitude
            bookmark.latitude = latLng.latitude
            bookmark.category = "Other"
            return bookmarkRepo.addBookmark(bookmark)
        }
        }