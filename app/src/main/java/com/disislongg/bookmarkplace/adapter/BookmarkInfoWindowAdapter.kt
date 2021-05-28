package com.disislongg.bookmarkplace.adapter

import android.app.Activity
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.disislongg.bookmarkplace.R
import com.disislongg.bookmarkplace.ui.MapsActivity
import com.disislongg.bookmarkplace.viewmodel.MapsViewModel
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.Marker

class BookmarkInfoWindowAdapter(val context: Activity):
    GoogleMap.InfoWindowAdapter {
    private val contents: View
    private val window: View

    init {
        contents = context.layoutInflater.inflate(
            R.layout.content_bookmark_info, null
        )

        window = context.layoutInflater.inflate(
            R.layout.window_bookmark_info, null
        )
    }

    override fun getInfoWindow(p0: Marker?): View? {
        /*val titleView = window.findViewById<TextView>(R.id.title)
        if (p0 != null) {
            titleView.text = p0.title ?: ""
        }

        val phoneView = window.findViewById<TextView>(R.id.phone)
        if (p0 != null) {
            phoneView.text = p0.snippet ?: ""
        }

        val imageView = window.findViewById<ImageView>(R.id.photo)
        if (p0 != null) {
            imageView.setImageBitmap(p0.tag as Bitmap)
        }

        return window*/
        return null
    }

    override fun getInfoContents(p0: Marker?): View {
        val titleView = contents.findViewById<TextView>(R.id.title)
        if (p0 != null) {
            titleView.text = p0.title ?: ""
        }

        val phoneView = contents.findViewById<TextView>(R.id.phone)
        if (p0 != null) {
            phoneView.text = p0.snippet ?: ""
        }

        val imageView = contents.findViewById<ImageView>(R.id.photo)
        if (p0 != null) {
            //imageView.setImageBitmap((p0.tag as MapsActivity.PlaceInfo).image)
            when(p0.tag) {
                is MapsActivity.PlaceInfo -> {
                    imageView.setImageBitmap((p0.tag as MapsActivity.PlaceInfo).image)
                }
                is MapsViewModel.BookmarkView -> {
                    var bookMarkView = p0.tag as MapsViewModel.BookmarkView
                    imageView.setImageBitmap(bookMarkView.getImage(context))
                }
            }
        }

        return contents
    }
}