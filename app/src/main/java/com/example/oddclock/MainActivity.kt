package com.example.oddclock

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.RadioButton
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private data class City(val name: String, val lat: Float, val lon: Float)

    private val cities = listOf(
        City("Dehugaon", 18.72f, 73.77f),
        City("Pimpri-Chinchwad", 18.63f, 73.80f),
        City("Pune", 18.52f, 73.86f),
        City("Mumbai", 19.08f, 72.88f),
        City("Nashik", 20.00f, 73.79f),
        City("Kolhapur", 16.70f, 74.24f),
        City("Chh. Sambhajinagar", 19.88f, 75.34f),
        City("Nagpur", 21.15f, 79.09f),
        City("Custom (lat/lon)", 0f, 0f)
    )

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var holder: FrameLayout
    private lateinit var etLat: EditText
    private lateinit var etLon: EditText
    private lateinit var spCity: Spinner
    private lateinit var tvStatus: TextView
    private var ready = false

    private val ticker = object : Runnable {
        override fun run() {
            refreshPreview()
            handler.postDelayed(this, 60000L - System.currentTimeMillis() % 60000L + 100L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        holder = findViewById(R.id.preview_holder)
        etLat = findViewById(R.id.et_lat)
        etLon = findViewById(R.id.et_lon)
        spCity = findViewById(R.id.sp_city)
        tvStatus = findViewById(R.id.tv_status)

        val swDark = findViewById<Switch>(R.id.sw_dark)
        val swMr = findViewById<Switch>(R.id.sw_marathi)
        val swF = findViewById<Switch>(R.id.sw_fahrenheit)
        val rbNext = findViewById<RadioButton>(R.id.rb_next)
        val rbPrev = findViewById<RadioButton>(R.id.rb_prev)

        swDark.isChecked = Prefs.dark(this)
        swMr.isChecked = Prefs.marathi(this)
        swF.isChecked = Prefs.fahrenheit(this)
        rbPrev.isChecked = Prefs.previousOdd(this)
        rbNext.isChecked = !Prefs.previousOdd(this)

        swDark.setOnCheckedChangeListener { _, v -> Prefs.setDark(this, v); changed() }
        swMr.setOnCheckedChangeListener { _, v -> Prefs.setMarathi(this, v); changed() }
        swF.setOnCheckedChangeListener { _, v -> Prefs.setFahrenheit(this, v); changed() }
        rbPrev.setOnCheckedChangeListener { _, v -> Prefs.setPreviousOdd(this, v); changed() }

        // city spinner
        spCity.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, cities.map { it.name })
        val idx = cities.indexOfFirst { it.name == Prefs.city(this) }.let { if (it < 0) cities.size - 1 else it }
        spCity.setSelection(idx)
        etLat.setText(Prefs.lat(this).toString())
        etLon.setText(Prefs.lon(this).toString())
        spCity.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (!ready) return
                val c = cities[pos]
                if (c.name.startsWith("Custom")) return
                etLat.setText(c.lat.toString())
                etLon.setText(c.lon.toString())
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        ready = true

        findViewById<View>(R.id.btn_save).setOnClickListener { saveLocation() }
        findViewById<View>(R.id.btn_add).setOnClickListener { addWidget() }

        // keep the alarm chain alive if a widget already exists
        OddClockWidget.scheduleNext(this)
        refreshWeather(force = false)
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }

    private fun changed() {
        refreshPreview()
        WidgetRenderer.updateAll(this)
    }

    private fun refreshPreview() {
        val rv = WidgetRenderer.build(this)
        holder.removeAllViews()
        val v = rv.apply(applicationContext, holder)
        val h = (130 * resources.displayMetrics.density).toInt()
        holder.addView(v, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, h))
        updateStatus()
    }

    private fun updateStatus() {
        val s = WeatherRepo.cached(this)
        tvStatus.text = if (s == null) "हवामान अजून आलेलं नाही (इंटरनेट चालू ठेवा)"
        else "शेवटचं अपडेट: " + SimpleDateFormat("h:mm a", Locale.US).format(Date(s.fetchedAt)) +
            "  •  ${Prefs.city(this)}"
    }

    private fun saveLocation() {
        val lat = etLat.text.toString().toFloatOrNull()
        val lon = etLon.text.toString().toFloatOrNull()
        if (lat == null || lon == null || lat !in -90f..90f || lon !in -180f..180f) {
            Toast.makeText(this, "Latitude/Longitude बरोबर टाका", Toast.LENGTH_SHORT).show()
            return
        }
        val name = cities[spCity.selectedItemPosition].name.let {
            if (it.startsWith("Custom")) "Custom" else it
        }
        Prefs.setLocation(this, name, lat, lon)
        WeatherRepo.clear(this)
        changed()
        refreshWeather(force = true)
    }

    private fun refreshWeather(force: Boolean) {
        tvStatus.text = "हवामान आणत आहे…"
        Thread {
            val ok = WeatherRepo.refreshIfStale(this, force)
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                changed()
                if (force && !ok) Toast.makeText(this, "हवामान मिळालं नाही, इंटरनेट तपासा", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    private fun addWidget() {
        val mgr = AppWidgetManager.getInstance(this)
        if (mgr.isRequestPinAppWidgetSupported) {
            mgr.requestPinAppWidget(ComponentName(this, OddClockWidget::class.java), null, null)
        } else {
            Toast.makeText(this, "होमस्क्रीनवर जास्त वेळ दाबा → Widgets → Odd Clock", Toast.LENGTH_LONG).show()
        }
    }
}
