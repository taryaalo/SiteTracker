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
        listOf("🏗️ إدارة المشاريع" to ::showProjects, "📍 إدارة المواقع" to ::showSites, "📤 تصدير واستيراد" to ::showExport).forEach { (label, action) ->
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
                    row.addView(Button(this).apply { text = "🗑️"; textSize = 11f; setTextColor(0xFFff6b35.toInt
