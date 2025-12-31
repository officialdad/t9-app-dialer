package com.t9dialer

import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView

class T9Activity : Activity() {

    private lateinit var searchInput: EditText
    private lateinit var appsList: ListView
    private lateinit var allApps: List<AppInfo>
    private lateinit var adapter: ArrayAdapter<String>

    data class AppInfo(val name: String, val packageName: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_t9)

        searchInput = findViewById(R.id.searchInput)
        appsList = findViewById(R.id.appsList)

        // Load all installed apps
        loadInstalledApps()

        // Set up adapter
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, allApps.map { it.name })
        appsList.adapter = adapter

        // Set up search listener
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString()
                filterApps(query)
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // Launch app on click
        appsList.setOnItemClickListener { _, _, position, _ ->
            val filteredApps = if (searchInput.text.isEmpty()) {
                allApps
            } else {
                allApps.filter { matchesT9(it.name, searchInput.text.toString()) }
            }

            if (position < filteredApps.size) {
                launchApp(filteredApps[position].packageName)
            }
        }
    }

    private fun loadInstalledApps() {
        val pm = packageManager
        val packages = pm.getInstalledApplications(0)

        allApps = packages
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
                     pm.getLaunchIntentForPackage(it.packageName) != null }
            .map { AppInfo(pm.getApplicationLabel(it).toString(), it.packageName) }
            .sortedBy { it.name }
    }

    private fun filterApps(query: String) {
        if (query.isEmpty()) {
            adapter.clear()
            adapter.addAll(allApps.map { it.name })
        } else {
            val filtered = allApps
                .filter { matchesT9(it.name, query) }
                .map { it.name }
            adapter.clear()
            adapter.addAll(filtered)
        }
        adapter.notifyDataSetChanged()
    }

    private fun matchesT9(appName: String, query: String): Boolean {
        val t9Map = mapOf(
            '2' to "abc", '3' to "def", '4' to "ghi",
            '5' to "jkl", '6' to "mno", '7' to "pqrs",
            '8' to "tuv", '9' to "wxyz", '0' to " "
        )

        val cleanName = appName.lowercase()
        var nameIndex = 0

        for (digit in query) {
            val letters = t9Map[digit] ?: continue

            while (nameIndex < cleanName.length &&
                   !letters.contains(cleanName[nameIndex])) {
                nameIndex++
            }

            if (nameIndex >= cleanName.length) {
                return false
            }

            nameIndex++
        }

        return true
    }

    private fun launchApp(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            startActivity(intent)
            finish()
        }
    }
}
