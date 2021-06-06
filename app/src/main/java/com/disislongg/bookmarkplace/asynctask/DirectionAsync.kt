package com.disislongg.bookmarkplace.asynctask
import android.graphics.Color
import android.os.AsyncTask
import android.util.Log
import com.disislongg.bookmarkplace.ui.MapsActivity
import com.google.android.gms.maps.model.PolylineOptions
import com.google.maps.android.PolyUtil
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.Exception
import java.lang.StringBuilder
import java.net.HttpURLConnection
import java.net.URL

class DirectionAsync: AsyncTask<String, Void, java.util.ArrayList<String>>() {
    override fun onPreExecute() {
        super.onPreExecute()
    }

    override fun doInBackground(vararg params: String?): java.util.ArrayList<String>? {
        //Nhận đường dẫn api để lấy kết quả chỉ đường
        val url = URL(params[0])
        //Mở kết nối httpURLConnection
        val httpURLConnection = url.openConnection() as HttpURLConnection

        //Bắt đầu đọc dữ liệu mà api trả về và lưu vào string builder
        try {
            val bufferedReader = BufferedReader(
                InputStreamReader(httpURLConnection.inputStream, "utf-8")
            )

            var sb = StringBuilder()
            var response: String? = null
            while (bufferedReader.readLine().also { response = it } != null) {
                sb.append(response?.trim() ?: "")
            }
            Log.e("JSON RESULT", sb.toString())

            //Xử lý dữ liệu api trả về
            val jsonObjects = JSONObject(sb.toString())
            //Dữ liệu trả về có rất nhiều trường, ta chỉ cần lấy dữ liệu
            //về các địa điểm nằm giữa đường đi giữa 2 điểm nguồn và đích
            val jsonArray = jsonObjects.getJSONArray("routes").getJSONObject(0)
                .getJSONArray("legs").getJSONObject(0).getJSONArray("steps")

            //Dữ liệu về các địa điểm sẽ được lưu vào list polyline để sau đó
            //ta sẽ dùng nó để vẽ line trên google maps
            val count = jsonArray.length()
            var polyline_array = ArrayList<String>(count)

            var jsonObject2 = JSONObject()

            for (i in 0 until count) {
                jsonObject2 = jsonArray.getJSONObject(i)
                val polygon = jsonObject2.getJSONObject("polyline").getString("points")
                polyline_array.add(polygon)
            }

            Log.e("JSON RESULT", polyline_array[1].toString())
            return polyline_array

        }catch (ex: Exception) {
            Log.e("ERROR JSON", ex.message.toString())
            return null
        }
    }

    override fun onProgressUpdate(vararg values: Void?) {
        super.onProgressUpdate(*values)
    }

    override fun onPostExecute(result: java.util.ArrayList<String>?) {
        //Lấy dữ liệu về các địa điểm cần phải đi qua chuyển sang dạng
        //toạ độ và vẽ lên bản đồ
        if (result != null) {
            for(i in 0 until result.size) {
                val option2 = PolylineOptions()
                option2.color(Color.BLUE)
                option2.width(10F)
                option2.addAll(PolyUtil.decode(result?.get(i)))
                MapsActivity.map.addPolyline(option2)
            }
        }
    }
}