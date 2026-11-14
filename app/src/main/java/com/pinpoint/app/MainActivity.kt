package com.taskly.app

import android.app.DatePickerDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.liquidglass.LiquidGlassView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

data class Task(
    val id: String = UUID.randomUUID().toString(),
    var title: String,
    var dueDate: Long? = null,
    var category: String = "Egyéb",
    var done: Boolean = false
)

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var taskCountText: TextView
    private lateinit var fabGlass: LiquidGlassView
    private lateinit var headerGlass: LiquidGlassView
    private lateinit var filterAll: TextView
    private lateinit var filterActive: TextView
    private lateinit var filterDone: TextView

    private val allTasks = mutableListOf<Task>()
    private var currentFilter = "ALL" // ALL, ACTIVE, DONE
    private lateinit var adapter: TaskAdapter

    private val categories = listOf("Személyes", "Munka", "Otthon", "Egészség", "Egyéb")
    private val categoryColors = mapOf(
        "Személyes" to "#4FC3F7",
        "Munka" to "#BA68C8",
        "Otthon" to "#81C784",
        "Egészség" to "#E57373",
        "Egyéb" to "#90A4AE"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.recyclerView)
        emptyText = findViewById(R.id.emptyText)
        taskCountText = findViewById(R.id.taskCountText)
        fabGlass = findViewById(R.id.fabGlass)
        headerGlass = findViewById(R.id.headerGlass)
        filterAll = findViewById(R.id.filterAll)
        filterActive = findViewById(R.id.filterActive)
        filterDone = findViewById(R.id.filterDone)

        fabGlass.enableDynamicBackground = true
        headerGlass.enableDynamicBackground = true

        loadTasks()

        adapter = TaskAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        setupFilters()
        updateUI()

        fabGlass.setOnClickListener { showTaskDialog(null) }
    }

    private fun setupFilters() {
        filterAll.setOnClickListener { setFilter("ALL") }
        filterActive.setOnClickListener { setFilter("ACTIVE") }
        filterDone.setOnClickListener { setFilter("DONE") }
    }

    private fun setFilter(filter: String) {
        currentFilter = filter
        filterAll.setTextColor(if (filter == "ALL") Color.WHITE else Color.parseColor("#888888"))
        filterActive.setTextColor(if (filter == "ACTIVE") Color.WHITE else Color.parseColor("#888888"))
        filterDone.setTextColor(if (filter == "DONE") Color.WHITE else Color.parseColor("#888888"))
        adapter.notifyDataSetChanged()
        updateUI()
    }

    private fun getFilteredTasks(): List<Task> {
        return when (currentFilter) {
            "ACTIVE" -> allTasks.filter { !it.done }
            "DONE" -> allTasks.filter { it.done }
            else -> allTasks
        }
    }

    private fun updateUI() {
        val activeCount = allTasks.count { !it.done }
        taskCountText.text = when {
            activeCount == 0 -> "Nincs aktív feladat"
            activeCount == 1 -> "1 aktív feladat"
            else -> "$activeCount aktív feladat"
        }
        emptyText.visibility = if (getFilteredTasks().isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showTaskDialog(editTask: Task?) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        val titleInput = EditText(this).apply {
            hint = "Feladat neve..."
            setText(editTask?.title ?: "")
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setBackgroundColor(Color.parseColor("#22FFFFFF"))
            setPadding(36, 32, 36, 32)
            textSize = 16f
        }
        container.addView(titleInput)

        val catLabel = TextView(this).apply {
            text = "Kategória"
            setTextColor(Color.LTGRAY)
            textSize = 13f
            setPadding(0, 32, 0, 12)
        }
        container.addView(catLabel)

        val categoryGroup = RadioGroup(this).apply { orientation = LinearLayout.VERTICAL }
        var selectedCategory = editTask?.category ?: "Egyéb"

        categories.forEach { cat ->
            val rb = RadioButton(this).apply {
                text = "  $cat"
                setTextColor(Color.WHITE)
                buttonTintList = android.content.res.ColorStateList.valueOf(
                    Color.parseColor(categoryColors[cat] ?: "#90A4AE")
                )
                isChecked = cat == selectedCategory
                setOnClickListener { selectedCategory = cat }
                setPadding(0, 12, 0, 12)
            }
            categoryGroup.addView(rb)
        }
        container.addView(categoryGroup)

        var selectedDate = editTask?.dueDate
        val dateBtn = Button(this).apply {
            text = selectedDate?.let {
                "Határidő: ${SimpleDateFormat("yyyy. MM. dd.", Locale.getDefault()).format(Date(it))}"
            } ?: "Határidő hozzáadása"
            setBackgroundColor(Color.parseColor("#33FFFFFF"))
            setTextColor(Color.WHITE)
            setPadding(0, 28, 0, 28)
            setOnClickListener {
                val cal = Calendar.getInstance()
                selectedDate?.let { cal.timeInMillis = it }
                DatePickerDialog(this@MainActivity, { _, y, m, d ->
                    cal.set(y, m, d, 23, 59)
                    selectedDate = cal.timeInMillis
                    text = "Határidő: ${SimpleDateFormat("yyyy. MM. dd.", Locale.getDefault()).format(Date(selectedDate!!))}"
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
            }
        }
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = 28
        container.addView(dateBtn, lp)

        MaterialAlertDialogBuilder(this)
            .setTitle(if (editTask == null) "Új feladat" else "Szerkesztés")
            .setView(container)
            .setPositiveButton("Mentés") { _, _ ->
                val title = titleInput.text.toString().trim()
                if (title.isEmpty()) {
                    Toast.makeText(this, "Adj meg egy feladatot", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (editTask == null) {
                    allTasks.add(0, Task(title = title, category = selectedCategory, dueDate = selectedDate))
                } else {
                    editTask.title = title
                    editTask.category = selectedCategory
                    editTask.dueDate = selectedDate
                }
                saveTasks()
                adapter.notifyDataSetChanged()
                updateUI()
            }
            .setNegativeButton("Mégse", null)
            .apply {
                if (editTask != null) {
                    setNeutralButton("Törlés") { _, _ ->
                        allTasks.remove(editTask)
                        saveTasks()
                        adapter.notifyDataSetChanged()
                        updateUI()
                    }
                }
            }
            .show()
    }

    private fun saveTasks() {
        val arr = JSONArray()
        allTasks.forEach { t ->
            arr.put(JSONObject().apply {
                put("id", t.id)
                put("title", t.title)
                put("dueDate", t.dueDate ?: JSONObject.NULL)
                put("category", t.category)
                put("done", t.done)
            })
        }
        getSharedPreferences("taskly", Context.MODE_PRIVATE)
            .edit().putString("tasks", arr.toString()).apply()
    }

    private fun loadTasks() {
        val json = getSharedPreferences("taskly", Context.MODE_PRIVATE).getString("tasks", "[]") ?: "[]"
        val arr = JSONArray(json)
        allTasks.clear()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            allTasks.add(Task(
                id = o.getString("id"),
                title = o.getString("title"),
                dueDate = if (o.isNull("dueDate")) null else o.getLong("dueDate"),
                category = o.optString("category", "Egyéb"),
                done = o.optBoolean("done", false)
            ))
        }
    }

    inner class TaskAdapter : RecyclerView.Adapter<TaskAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val glass = v as LiquidGlassView
            val title: TextView = v.findViewById(R.id.taskTitle)
            val meta: TextView = v.findViewById(R.id.taskMeta)
            val check: CheckBox = v.findViewById(R.id.taskCheck)
            val dot: View = v.findViewById(R.id.categoryDot)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_task, parent, false)
            (v as LiquidGlassView).enableDynamicBackground = true
            return VH(v)
        }

        override fun getItemCount() = getFilteredTasks().size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val task = getFilteredTasks()[position]

            holder.title.text = task.title
            if (task.done) {
                holder.title.paintFlags = holder.title.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                holder.title.setTextColor(Color.parseColor("#88FFFFFF"))
            } else {
                holder.title.paintFlags = holder.title.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                holder.title.setTextColor(Color.WHITE)
            }

            val color = Color.parseColor(categoryColors[task.category] ?: "#90A4AE")
            holder.dot.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }

            val dueStr = task.dueDate?.let {
                val sdf = SimpleDateFormat("MMM d.", Locale("hu"))
                val formatted = sdf.format(Date(it))
                if (it < System.currentTimeMillis() && !task.done) "Lejárt • $formatted" else formatted
            } ?: "Nincs határidő"

            holder.meta.text = "${task.category}  ·  $dueStr"
            holder.meta.setTextColor(
                if (task.dueDate != null && task.dueDate!! < System.currentTimeMillis() && !task.done)
                    Color.parseColor("#FF8A80") else Color.parseColor("#B0B0C0")
            )

            holder.check.setOnCheckedChangeListener(null)
            holder.check.isChecked = task.done
            holder.check.setOnCheckedChangeListener { _, checked ->
                task.done = checked
                saveTasks()
                notifyDataSetChanged()
                updateUI()
            }

            holder.itemView.setOnClickListener { showTaskDialog(task) }
        }
    }
}
