package com.disislongg.bookmarkplace.asynctask

import android.content.Context
import android.graphics.Color
import android.os.AsyncTask
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.PolylineOptions
import com.google.maps.android.PolyUtil
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.util.*
import kotlin.collections.ArrayList

class GetDirectionsData(val context: Context): AsyncTask<Objects, String, String>() {
    private lateinit var map: GoogleMap
    private lateinit var url: String
    private lateinit var startLatLng: LatLng
    private lateinit var endLatLng: LatLng
    private var httpURLConnection: HttpURLConnection? = null
    private var data: String = ""
    private var inputStream: InputStream? = null

    override fun doInBackground(vararg params: Objects?): String {
        map = params[0] as GoogleMap
        url = params[1] as String
        startLatLng = params[2] as LatLng
        endLatLng = params[3] as LatLng

        try {
            val myURL = URL(url)
            httpURLConnection = myURL.openConnection() as HttpURLConnection?
            httpURLConnection?.connect()

            inputStream = httpURLConnection?.inputStream
            val bufferedReader = BufferedReader(InputStreamReader(inputStream))
            val stringBuffer = StringBuffer()
            var line: String = ""

            do {
                line = bufferedReader.readLine()
                if(line == null) {
                    break
                }
            } while (true)
            data = stringBuffer.toString()
            bufferedReader.close()
        } catch (ex: MalformedURLException) {
            ex.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return data
    }

    override fun onProgressUpdate(vararg values: String?) {
        super.onProgressUpdate(*values)
    }

    override fun onPostExecute(result: String?) {
        try {
            val jsonObjects = JSONObject(result)
            val jsonArray = jsonObjects.getJSONArray("routes").getJSONObject(0)
                .getJSONArray("legs").getJSONObject(0).getJSONArray("steps")

            val count = jsonArray.length()
            val polyline_array = ArrayList<String>(count)

            var jsonObject2 = JSONObject()
            for(i in 0 until count) {
                jsonObject2 = jsonArray.getJSONObject(i)

                val polygone = jsonObject2.getJSONObject("polyline").getString("points")
                polyline_array.add(polygone)
            }

            val count2 = polyline_array.size
            for(i in 0 until count2) {
                val option2 = PolylineOptions()
                option2.color(Color.BLUE)
                option2.width(10F)
                option2.addAll(PolyUtil.decode(polyline_array[i]))
                map.addPolyline(option2)
            }
        } catch (ex: JSONException) {
            ex.printStackTrace()
        }
    }
}