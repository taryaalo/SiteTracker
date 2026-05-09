package com.sitetracker.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

data class Project(val id: String = UUID.randomUUID().toString(), var name: String, var description: String = "", val createdAt: String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()))
data class Site(val id: String = UUID.randomUUID().toString(), var projectId: String, var name: String, var lat: Double, var lng: Double, var alt: Double = 0.0, var acc: Float = 0f, var sats: Int = 0, var notes: String = "", val createdAt: String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()))
data class AppData(val projects: MutableList<Project> = mutableListOf(), val sites: MutableList<Site> = mutableListOf())

class MainActivity : AppCompatActivity() {
    private val gson = Gson()
    private val dataFile by lazy { File(filesDir, "data.json") }
    private var appData = AppData()
    private lateinit var locationManager: LocationManager
    private var capturedLat = 0.0; private var capturedLng = 0.0
    private var capturedAlt = 0.0; private var capturedAcc = 0f; private var capturedSats = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        loadData()
        showMainMenu()
    }

    private fun loadData() {
        if (dataFile.exists()) {
            try { appData = gson.fromJson(dataFile.readText(), AppData::class.java) ?: AppData() }
            catch (e: Exception) { appData = AppData() }
        }
    }

    private fun saveData() { dataFile.writeText(gson.toJson(appData)) }

    private fun showMainMenu() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(40, 60, 40, 40)
            setBackgroundColor(0xFF0a0f1e.toInt())
        }
        val title = TextView(this).apply {
            text = "🛰️ مواقع المشاريع"; textSize = 26f; setTextColor(0xFF00c9ff.toInt())
            gravity = android.view.Gravity.CENTER; setPadding(0, 0, 0, 8)
        }
        val sub = TextView(this).apply {
            text = "إدارة مواقع المشاريع الميدانية"; textSize = 14f
            setTextColor(0xFF8899bb.toInt()); gravity = android.view.Gravity.CENTER; setPadding(0, 0, 0, 40)
        }
        val stats = TextView(this).apply {
            text = "📁 ${appData.projects.size} مشروع   |   📍 ${appData.sites.size} موقع"
            textSize = 14f; setTextColor(0xFF00e676.toInt()); gravity = android.view.Gravity.CENTER; setPadding(0, 0, 0, 40)
        }
        layout.addView(title); layout.addView(sub); layout.addView(stats)
        listOf("🏗️ إدارة المشاريع" to ::showProjects, "📍 إدارة المواقع" to ::showSites, "🗺️ خريطة المواقع" to ::showMap, "📤 تصدير واستيراد" to ::showExport).forEach { (label, action) ->
            val btn = Button(this).apply {
                text = label; textSize = 16f; setTextColor(0xFFFFFFFF.toInt())
                setBackgroundColor(0xFF1a2235.toInt()); setPadding(20, 20, 20, 20)
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 12, 0, 0) }
                setOnClickListener { action() }
            }
            layout.addView(btn)
        }
        setContentView(ScrollView(this).apply { addView(layout); setBackgroundColor(0xFF0a0f1e.toInt()) })
    }

    private fun showMap() {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFF0a0f1e.toInt()) }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(30, 40, 30, 20); setBackgroundColor(0xFF111827.toInt()) }
        val back = Button(this).apply { text = "← رجوع"; setTextColor(0xFF00c9ff.toInt()); setBackgroundColor(0xFF1a2235.toInt()); setOnClickListener { showMainMenu() } }
        val title = TextView(this).apply { text = "🗺️ خريطة المواقع"; textSize = 20f; setTextColor(0xFFe8f0fe.toInt()); setPadding(20, 0, 0, 0); gravity = android.view.Gravity.CENTER_VERTICAL }
        header.addView(back); header.addView(title)
        layout.addView(header)

        val webView = android.webkit.WebView(this).apply {
            settings.javaScriptEnabled = true
            layoutParams = LinearLayout.LayoutParams(-1, -1)
        }

        if (appData.sites.isEmpty()) {
            layout.addView(TextView(this).apply { text = "لا توجد مواقع لعرضها"; setTextColor(0xFF8899bb.toInt()); gravity = android.view.Gravity.CENTER; setPadding(0, 40, 0, 0) })
        } else {
            val sitesJson = gson.toJson(appData.sites.map { s ->
                val p = appData.projects.find { it.id == s.projectId }
                mapOf("name" to s.name, "proj" to (p?.name ?: "—"), "lat" to s.lat, "lng" to s.lng)
            })
            val html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="utf-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
                    <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
                    <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
                    <style>
                        body { padding: 0; margin: 0; }
                        html, body, #map { height: 100%; width: 100vw; }
                    </style>
                </head>
                <body>
                    <div id="map"></div>
                    <script>
                        var map = L.map('map').setView([${appData.sites.first().lat}, ${appData.sites.first().lng}], 13);
                        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                            maxZoom: 19,
                            attribution: '© OpenStreetMap'
                        }).addTo(map);

                        var sites = $sitesJson;
                        var bounds = [];

                        sites.forEach(function(site) {
                            var marker = L.marker([site.lat, site.lng]).addTo(map);
                            marker.bindPopup("<b>" + site.name + "</b><br>🏗️ " + site.proj);
                            bounds.push([site.lat, site.lng]);
                        });

                        if (bounds.length > 0) {
                            map.fitBounds(bounds);
                        }
                    </script>
                </body>
                </html>
            """.trimIndent()
            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            layout.addView(webView)
        }
        setContentView(layout)
    }

    private fun showProjects() {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(30, 40, 30, 40); setBackgroundColor(0xFF0a0f1e.toInt()) }
        val back = Button(this).apply { text = "← رجوع"; setTextColor(0xFF00c9ff.toInt()); setBackgroundColor(0xFF111827.toInt()); setOnClickListener { showMainMenu() } }
        val title = TextView(this).apply { text = "🏗️ المشاريع"; textSize = 22f; setTextColor(0xFFe8f0fe.toInt()); setPadding(0, 16, 0, 16) }
        val addBtn = Button(this).apply { text = "➕ مشروع جديد"; setTextColor(0xFF000000.toInt()); setBackgroundColor(0xFF00c9ff.toInt()); setOnClickListener { showAddProjectDialog() } }
        layout.addView(back); layout.addView(title); layout.addView(addBtn)
        if (appData.projects.isEmpty()) {
            layout.addView(TextView(this).apply { text = "لا توجد مشاريع بعد"; setTextColor(0xFF8899bb.toInt()); gravity = android.view.Gravity.CENTER; setPadding(0, 40, 0, 0) })
        } else {
            appData.projects.forEach { p ->
                val cnt = appData.sites.count { it.projectId == p.id }
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFF111827.toInt()); setPadding(20, 16, 20, 16)
                    layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 12, 0, 0) }
                }
                card.addView(TextView(this).apply { text = "🏗️ ${p.name}"; textSize = 16f; setTextColor(0xFFe8f0fe.toInt()) })
                card.addView(TextView(this).apply { text = "📍 $cnt موقع   |   📅 ${p.createdAt}"; textSize = 12f; setTextColor(0xFF8899bb.toInt()) })
                if (p.description.isNotEmpty()) card.addView(TextView(this).apply { text = p.description; textSize = 12f; setTextColor(0xFF8899bb.toInt()) })
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 8, 0, 0) } }
                row.addView(Button(this).apply { text = "🗑️ حذف"; textSize = 12f; setTextColor(0xFFff6b35.toInt()); setBackgroundColor(0xFF1a2235.toInt()); setOnClickListener { if (android.app.AlertDialog.Builder(this@MainActivity).setTitle("حذف المشروع").setMessage("سيتم حذف جميع مواقعه").setPositiveButton("حذف") { _, _ -> appData.projects.remove(p); appData.sites.removeAll { it.projectId == p.id }; saveData(); showProjects() }.setNegativeButton("إلغاء", null).create().also { it.show() } != null) {} } })
                card.addView(row); layout.addView(card)
            }
        }
        setContentView(ScrollView(this).apply { addView(layout); setBackgroundColor(0xFF0a0f1e.toInt()) })
    }

    private fun showAddProjectDialog() {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 20, 20, 0) }
        val nameEt = EditText(this).apply { hint = "اسم المشروع"; setTextColor(0xFF000000.toInt()) }
        val descEt = EditText(this).apply { hint = "الوصف (اختياري)"; setTextColor(0xFF000000.toInt()) }
        layout.addView(TextView(this).apply { text = "اسم المشروع"; textSize = 14f }); layout.addView(nameEt)
        layout.addView(TextView(this).apply { text = "الوصف"; textSize = 14f; setPadding(0, 12, 0, 0) }); layout.addView(descEt)
        AlertDialog.Builder(this).setTitle("🏗️ مشروع جديد").setView(layout)
            .setPositiveButton("إضافة") { _, _ -> val n = nameEt.text.toString().trim(); if (n.isNotEmpty()) { appData.projects.add(Project(name = n, description = descEt.text.toString().trim())); saveData(); showProjects() } else Toast.makeText(this, "أدخل اسم المشروع", Toast.LENGTH_SHORT).show() }
            .setNegativeButton("إلغاء", null).show()
    }

    private fun showSites() {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(30, 40, 30, 40); setBackgroundColor(0xFF0a0f1e.toInt()) }
        val back = Button(this).apply { text = "← رجوع"; setTextColor(0xFF00c9ff.toInt()); setBackgroundColor(0xFF111827.toInt()); setOnClickListener { showMainMenu() } }
        val title = TextView(this).apply { text = "📍 المواقع"; textSize = 22f; setTextColor(0xFFe8f0fe.toInt()); setPadding(0, 16, 0, 16) }
        layout.addView(back); layout.addView(title)
        if (appData.projects.isEmpty()) {
            layout.addView(TextView(this).apply { text = "⚠️ أضف مشروعاً أولاً"; setTextColor(0xFFff6b35.toInt()); gravity = android.view.Gravity.CENTER; setPadding(0, 40, 0, 0) })
        } else {
            val addBtn = Button(this).apply { text = "➕ موقع جديد"; setTextColor(0xFF000000.toInt()); setBackgroundColor(0xFF00c9ff.toInt()); setOnClickListener { showAddSiteDialog() } }
            layout.addView(addBtn)
            if (appData.sites.isEmpty()) {
                layout.addView(TextView(this).apply { text = "لا توجد مواقع بعد"; setTextColor(0xFF8899bb.toInt()); gravity = android.view.Gravity.CENTER; setPadding(0, 40, 0, 0) })
            } else {
                appData.sites.forEach { s ->
                    val proj = appData.projects.find { it.id == s.projectId }
                    val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFF111827.toInt()); setPadding(20, 16, 20, 16); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 12, 0, 0) } }
                    card.addView(TextView(this).apply { text = "📍 ${s.name}"; textSize = 16f; setTextColor(0xFFe8f0fe.toInt()) })
                    card.addView(TextView(this).apply { text = "🏗️ ${proj?.name ?: "—"}"; textSize = 12f; setTextColor(0xFF8899bb.toInt()) })
                    card.addView(TextView(this).apply { text = "${String.format("%.6f", s.lat)}, ${String.format("%.6f", s.lng)}"; textSize = 11f; setTextColor(0xFF00c9ff.toInt()) })
                    card.addView(TextView(this).apply { text = "🛰️ ${s.sats} أقمار   |   ± ${s.acc.toInt()}م دقة"; textSize = 11f; setTextColor(0xFF8899bb.toInt()) })
                    val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 8, 0, 0) }; setPadding(0, 4, 0, 0) }
                    row.addView(Button(this).apply { text = "🗺️ خرائط"; textSize = 11f; setTextColor(0xFFFFFFFF.toInt()); setBackgroundColor(0xFF1a2235.toInt()); setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=${s.lat},${s.lng}"))) } })
                    row.addView(Button(this).apply { text = "📏 مسافة"; textSize = 11f; setTextColor(0xFFFFFFFF.toInt()); setBackgroundColor(0xFF1a2235.toInt()); setOnClickListener { measureDistance(s) } })
                    row.addView(Button(this).apply { text = "🔗 مشاركة"; textSize = 11f; setTextColor(0xFFFFFFFF.toInt()); setBackgroundColor(0xFF1a2235.toInt()); setOnClickListener { shareSite(s, proj) } })
                    row.addView(Button(this).apply { text = "🗑️"; textSize = 11f; setTextColor(0xFFff6b35.toInt()); setBackgroundColor(0xFF1a2235.toInt()); setOnClickListener {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("حذف الموقع")
                            .setMessage("هل أنت متأكد من حذف موقع ${s.name}؟")
                            .setPositiveButton("حذف") { _, _ -> appData.sites.remove(s); saveData(); showSites() }
                            .setNegativeButton("إلغاء", null)
                            .show()
                    } })
                    card.addView(row); layout.addView(card)
                }
            }
        }
        setContentView(ScrollView(this).apply { addView(layout); setBackgroundColor(0xFF0a0f1e.toInt()) })
    }

    private fun showAddSiteDialog() {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 20, 20, 0) }
        val nameEt = EditText(this).apply { hint = "اسم الموقع"; setTextColor(0xFF000000.toInt()) }
        val projNames = appData.projects.map { it.name }.toTypedArray()
        val spinner = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, projNames) }
        val notesEt = EditText(this).apply { hint = "ملاحظات (اختياري)"; setTextColor(0xFF000000.toInt()) }
        val coordTv = TextView(this).apply { text = "لم يتم تحديد الموقع بعد"; setTextColor(0xFFff6b35.toInt()); gravity = android.view.Gravity.CENTER }
        val gpsBtn = Button(this).apply { text = "📡 تحديد الموقع عبر GPS"; setBackgroundColor(0xFF00c9ff.toInt()); setTextColor(0xFF000000.toInt()) }
        layout.addView(TextView(this).apply { text = "اسم الموقع"; textSize = 14f }); layout.addView(nameEt)
        layout.addView(TextView(this).apply { text = "المشروع"; textSize = 14f; setPadding(0, 12, 0, 0) }); layout.addView(spinner)
        layout.addView(gpsBtn); layout.addView(coordTv)
        layout.addView(TextView(this).apply { text = "ملاحظات"; textSize = 14f; setPadding(0, 12, 0, 0) }); layout.addView(notesEt)
        capturedLat = 0.0; capturedLng = 0.0
        gpsBtn.setOnClickListener { captureGPS(coordTv) }
        AlertDialog.Builder(this).setTitle("📍 موقع جديد").setView(layout)
            .setPositiveButton("إضافة") { _, _ ->
                val n = nameEt.text.toString().trim()
                if (n.isEmpty()) { Toast.makeText(this, "أدخل اسم الموقع", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                if (capturedLat == 0.0) { Toast.makeText(this, "حدد الموقع أولاً", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                val proj = appData.projects[spinner.selectedItemPosition]
                appData.sites.add(Site(projectId = proj.id, name = n, lat = capturedLat, lng = capturedLng, alt = capturedAlt, acc = capturedAcc, sats = capturedSats, notes = notesEt.text.toString().trim()))
                saveData(); showSites()
            }.setNegativeButton("إلغاء", null).show()
    }

    private fun captureGPS(tv: TextView) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1); return
        }
        tv.text = "🔄 جاري البحث عن الأقمار الصناعية..."; tv.setTextColor(0xFFffeb3b.toInt())
        val gnssCallback = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) { capturedSats = (0 until status.satelliteCount).count { status.usedInFix(it) } }
        }
        locationManager.registerGnssStatusCallback(gnssCallback, null)
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500, 0f, { loc ->
            capturedLat = loc.latitude; capturedLng = loc.longitude; capturedAlt = loc.altitude; capturedAcc = loc.accuracy
            val accColor = if (loc.accuracy < 10) 0xFF00e676.toInt() else if (loc.accuracy < 30) 0xFFffeb3b.toInt() else 0xFFff6b35.toInt()
            tv.setTextColor(accColor)
            tv.text = "🛰️ $capturedSats أقمار   |   ± ${loc.accuracy.toInt()}م\n${String.format("%.6f", loc.latitude)}, ${String.format("%.6f", loc.longitude)}"
            locationManager.unregisterGnssStatusCallback(gnssCallback)
        })
    }

    private fun measureDistance(s: Site) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "اسمح بالوصول للموقع", Toast.LENGTH_SHORT).show(); return
        }
        Toast.makeText(this, "⏳ جاري تحديد موقعك...", Toast.LENGTH_SHORT).show()

        var locationListener: android.location.LocationListener? = null
        locationListener = android.location.LocationListener { loc ->
            val R = 6371000.0
            val dLat = Math.toRadians(s.lat - loc.latitude); val dLon = Math.toRadians(s.lng - loc.longitude)
            val a = sin(dLat/2).pow(2) + cos(Math.toRadians(loc.latitude)) * cos(Math.toRadians(s.lat)) * sin(dLon/2).pow(2)
            val dist = R * 2 * atan2(sqrt(a), sqrt(1-a))
            val distStr = if (dist < 1000) "${dist.toInt()} متر" else "${"%.2f".format(dist/1000)} كيلومتر"
            runOnUiThread { AlertDialog.Builder(this).setTitle("📏 المسافة إلى ${s.name}").setMessage("المسافة: $distStr").setPositiveButton("حسناً", null).show() }

            locationListener?.let { locationManager.removeUpdates(it) }
        }

        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0f, locationListener)
    }

    private fun shareSite(s: Site, proj: Project?) {
        val text = "📍 ${s.name}\n🏗️ ${proj?.name ?: ""}\n🌐 ${String.format("%.6f", s.lat)}, ${String.format("%.6f", s.lng)}\n🛰️ ${s.sats} أقمار | ± ${s.acc.toInt()}م\n📝 ${s.notes}\n\nhttps://maps.google.com/?q=${s.lat},${s.lng}"
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, "مشاركة الموقع"))
    }

    private fun showExport() {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(30, 40, 30, 40); setBackgroundColor(0xFF0a0f1e.toInt()) }
        val back = Button(this).apply { text = "← رجوع"; setTextColor(0xFF00c9ff.toInt()); setBackgroundColor(0xFF111827.toInt()); setOnClickListener { showMainMenu() } }
        val title = TextView(this).apply { text = "📤 تصدير واستيراد"; textSize = 22f; setTextColor(0xFFe8f0fe.toInt()); setPadding(0, 16, 0, 24) }
        layout.addView(back); layout.addView(title)
        listOf(
            Triple("📤 تصدير JSON (مشاركة)", 0xFF00c9ff.toInt()) { exportJSON() },
            Triple("📊 تصدير CSV (Excel)", 0xFF00e676.toInt()) { exportCSV() },
            Triple("📥 استيراد JSON", 0xFF1a2235.toInt()) { importJSON() }
        ).forEach { (label, color, action) ->
            layout.addView(Button(this).apply {
                text = label; setTextColor(if (color == 0xFF1a2235.toInt()) 0xFFFFFFFF.toInt() else 0xFF000000.toInt())
                setBackgroundColor(color); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 12, 0, 0) }; setOnClickListener { action() }
            })
        }
        setContentView(ScrollView(this).apply { addView(layout); setBackgroundColor(0xFF0a0f1e.toInt()) })
    }

    private fun exportJSON() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "sitetracker_export.json")
        }
        startActivityForResult(intent, 101)
    }

    private fun exportCSV() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/csv"
            putExtra(Intent.EXTRA_TITLE, "sitetracker.csv")
        }
        startActivityForResult(intent, 102)
    }

    private fun importJSON() {
        startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).apply { type = "application/json" }, 100)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) {
            try {
                val text = contentResolver.openInputStream(data!!.data!!)!!.bufferedReader().readText()
                val type = object : TypeToken<Map<String, Any>>() {}.type
                val map: Map<String, Any> = gson.fromJson(text, type)
                val projJson = gson.toJson(map["projects"]); val sitesJson = gson.toJson(map["sites"])
                val projType = object : TypeToken<List<Project>>() {}.type
                val siteType = object : TypeToken<List<Site>>() {}.type
                val impProjects: List<Project> = gson.fromJson(projJson, projType)
                val impSites: List<Site> = gson.fromJson(sitesJson, siteType)
                val existIds = appData.projects.map { it.id }.toSet()
                val existSiteIds = appData.sites.map { it.id }.toSet()
                val newP = impProjects.filter { it.id !in existIds }
                val newS = impSites.filter { it.id !in existSiteIds }
                appData.projects.addAll(newP); appData.sites.addAll(newS); saveData()
                Toast.makeText(this, "✅ تم استيراد ${newP.size} مشروع و${newS.size} موقع", Toast.LENGTH_LONG).show()
                showMainMenu()
            } catch (e: Exception) { Toast.makeText(this, "❌ خطأ في الملف", Toast.LENGTH_SHORT).show() }
        } else if (requestCode == 101 && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                try {
                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        val json = gson.toJson(mapOf("projects" to appData.projects, "sites" to appData.sites, "exportedAt" to Date().toString(), "app" to "SiteTracker"))
                        outputStream.write(json.toByteArray())
                    }
                    Toast.makeText(this, "✅ تم تصدير JSON بنجاح", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "❌ خطأ في التصدير", Toast.LENGTH_SHORT).show()
                }
            }
        } else if (requestCode == 102 && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                try {
                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        val sb = StringBuilder("\uFEFF")
                        sb.appendLine("المشروع,اسم الموقع,خط العرض,خط الطول,الارتفاع,الدقة,الأقمار,الملاحظات,التاريخ")
                        appData.sites.forEach { s -> val p = appData.projects.find { it.id == s.projectId }; sb.appendLine("\"${p?.name}\",\"${s.name}\",${s.lat},${s.lng},${s.alt},${s.acc},${s.sats},\"${s.notes}\",${s.createdAt}") }
                        outputStream.write(sb.toString().toByteArray())
                    }
                    Toast.makeText(this, "✅ تم تصدير CSV بنجاح", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "❌ خطأ في التصدير", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) Toast.makeText(this, "✅ تم منح إذن الموقع", Toast.LENGTH_SHORT).show()
        else Toast.makeText(this, "⚠️ يجب منح إذن الموقع", Toast.LENGTH_SHORT).show()
    }
}
