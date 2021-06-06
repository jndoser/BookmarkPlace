package com.disislongg.bookmarkplace.repository

import android.content.Context
import androidx.lifecycle.LiveData
import com.disislongg.bookmarkplace.R
import com.disislongg.bookmarkplace.db.BookmarkDao
import com.disislongg.bookmarkplace.db.BookmarkPlaceDatabase
import com.disislongg.bookmarkplace.model.Bookmark
import com.disislongg.bookmarkplace.viewmodel.BookmarkDetailsViewModel
import com.google.android.libraries.places.api.model.Place
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class BookmarkRepo(val context: Context) {
    private var db = BookmarkPlaceDatabase.getInstance(context)
    private var bookmarkDao: BookmarkDao = db.bookmarkDao()
    private var categoryMap: HashMap<Int, String> = buildCategoryMap()
    private var allCategories: HashMap<String, Int> = buildCategories()
    val categories: List<String>
    get() = ArrayList(allCategories.keys)

    fun addBookmark(bookmark: Bookmark): Long? {
        val newId = bookmarkDao.insertBookmark(bookmark)
        bookmark.id = newId
        return newId
    }

    fun createBookmark(): Bookmark {
        return Bookmark()
    }

    val allBookmarks: LiveData<List<Bookmark>>
    get() {return bookmarkDao.loadAll()}

    fun getLiveBookmark(bookmarkId: Long): LiveData<Bookmark> {
        val bookmark = bookmarkDao.loadLiveBookmark(bookmarkId)
        return bookmark
    }

    fun updateBookmark(bookmark: Bookmark) {
        bookmarkDao.updateBookmark(bookmark)
    }

    fun getBookmark(bookmarkId: Long): Bookmark {
        return bookmarkDao.loadBookmark(bookmarkId)
    }

    private fun buildCategoryMap(): HashMap<Int, String> {
        return hashMapOf(
            Place.Type.BAKERY.ordinal to "Restaurant",
            Place.Type.BAR.ordinal to "Restaurant",
            Place.Type.CAFE.ordinal to "Restaurant",
            Place.Type.FOOD.ordinal to "Restaurant",
            Place.Type.RESTAURANT.ordinal to "Restaurant",
            Place.Type.MEAL_DELIVERY.ordinal to "Restaurant",
            Place.Type.MEAL_TAKEAWAY.ordinal to "Restaurant",
            Place.Type.GAS_STATION.ordinal to "Gas",
            Place.Type.CLOTHING_STORE.ordinal to "Shopping",
            Place.Type.DEPARTMENT_STORE.ordinal to "Shopping",
            Place.Type.FURNITURE_STORE.ordinal to "Shopping",
            Place.Type.GROCERY_OR_SUPERMARKET.ordinal to "Shopping",
            Place.Type.HARDWARE_STORE.ordinal to "Shopping",
            Place.Type.HOME_GOODS_STORE.ordinal to "Shopping",
            Place.Type.JEWELRY_STORE.ordinal to "Shopping",
            Place.Type.SHOE_STORE.ordinal to "Shopping",
            Place.Type.SHOPPING_MALL.ordinal to "Shopping",
            Place.Type.STORE.ordinal to "Shopping",
            Place.Type.LODGING.ordinal to "Lodging",
            Place.Type.ROOM.ordinal to "Lodging"
        )
    }

    fun placeTypeToCategory(placeType: Int): String {
        var category = "Other"
        if(categoryMap.containsKey(placeType)) {
            category = categoryMap[placeType].toString()
        }
        return category
    }

    private fun buildCategories(): HashMap<String, Int> {
        return hashMapOf(
            "Gas" to R.drawable.ic_gas,
            "Lodging" to R.drawable.ic_lodging,
            "Other" to R.drawable.ic_other,
            "Restaurant" to R.drawable.ic_restaurant,
            "Shopping" to R.drawable.ic_shopping
        )
    }

    fun getCategoryResourceId(placeCategory: String): Int? {
        return  allCategories[placeCategory]
    }

    fun deleteBookmark(bookmark: Bookmark) {
        bookmark.deleteImage(context)
        bookmarkDao.deleteBookmark(bookmark)
    }
}