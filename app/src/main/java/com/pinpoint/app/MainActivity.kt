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
import androidx.appcompat.app.AlertDialog
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
    private lateinit var fabGlass: LiquidGlassView
    private val tasks = mutableListOf<Task>()
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
        fabGlass = findViewById(R.id.fabGlass)
        fabGlass.enableDynamicBackground = true

        loadTasks()

        adapter = TaskAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        updateEmptyState()

        fabGlass.setOnClickListener { showTaskDialog(null) }
    }

    private fun updateEmptyState() {
        emptyText.visibility = if (tasks.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showTaskDialog(editTask: Task?) {
        val dialogView = LayoutInflater.from(this).inflate(android.R.layout.simple_list_item_1, null)

        // Custom layout for dialog
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
        }

        val titleInput = EditText(this).apply {
            hint = "Mit kell megcsinálni?"
            setText(editTask?.title ?: "")
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setBackgroundColor(Color.parseColor("#1E1E2E"))
            setPadding(32, 28, 32, 28)
            textSize = 16f
        }
        container.addView(titleInput)

        // Category selector
        val categoryLabel = TextView(this).apply {
            text = "Kategória"
            setTextColor(Color.LTGRAY)
            textSize = 13f
            setPadding(0, 28, 0, 12)
        }
        container.addView(categoryLabel)

        val categoryGroup = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
        }

        var selectedCategory = editTask?.category ?: "Egyéb"
        categories.forEach { cat ->
            val rb = RadioButton(this).apply {
                text = cat
                setTextColor(Color.WHITE)
                buttonTintList = android.content.res.ColorStateList.valueOf(Color.parseColor(categoryColors[cat] ?: "#90A4AE"))
                isChecked = cat == selectedCategory
                setOnClickListener { selectedCategory = cat }
            }
            categoryGroup.addView(rb)
        }
        container.addView(categoryGroup)

        // Date
        var selectedDate = editTask?.dueDate
        val dateButton = Button(this).apply {
            text = selectedDate?.let {
                "Határidő: ${SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(Date(it))}"
            } ?: "Határidő választása"
            setBackgroundColor(Color.parseColor("#2A2A3A"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                val cal = Calendar.getInstance()
                selectedDate?.let { cal.timeInMillis = it }
                DatePickerDialog(this@MainActivity, { _, y, m, d ->
                    cal.set(y, m, d, 23, 59)
                    selectedDate = cal.timeInMillis
                    text = "Határidő: ${SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(Date(selectedDate!!))}"
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
            }
        }
        val dateParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 28 }
        container.addView(dateButton, dateParams)

        MaterialAlertDialogBuilder(this)
            .setTitle(if (editTask == null) "Új feladat" else "Feladat szerkesztése")
            .setView(container)
            .setPositiveButton("Mentés") { _, _ ->
                val title = titleInput.text.toString().trim()
                if (title.isEmpty()) {
                    Toast.makeText(this, "Írj be egy feladatot!", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (editTask == null) {
                    tasks.add(0, Task(title = title, category = selectedCategory, dueDate = selectedDate))
                } else {
                    editTask.title = title
                    editTask.category = selectedCategory
                    editTask.dueDate = selectedDate
                }
                saveTasks()
                adapter.notifyDataSetChanged()
                updateEmptyState()
            }
            .setNegativeButton("Mégse", null)
            .setNeutralButton(if (editTask != null) "Törlés" else null) { _, _ ->
                if (editTask != null) {
                    tasks.remove(editTask)
                    saveTasks()
                    adapter.notifyDataSetChanged()
                    updateEmptyState()
                }
            }
            .show()
    }

    private fun saveTasks() {
        val arr = JSONArray()
        tasks.forEach { task ->
            arr.put(JSONObject().apply {
                put("id", task.id)
                put("title", task.title)
                put("dueDate", task.dueDate ?: JSONObject.NULL)
                put("category", task.category)
                put("done", task.done)
            })
        }
        getSharedPreferences("taskly", Context.MODE_PRIVATE)
            .edit()
            .putString("tasks", arr.toString())
            .apply()
    }

    private fun loadTasks() {
        val json = getSharedPreferences("taskly", Context.MODE_PRIVATE)
            .getString("tasks", "[]") ?: "[]"
        val arr = JSONArray(json)
        tasks.clear()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            tasks.add(
                Task(
                    id = obj.getString("id"),
                    title = obj.getString("title"),
                    dueDate = if (obj.isNull("dueDate")) null else obj.getLong("dueDate"),
                    category = obj.optString("category", "Egyéb"),
                    done = obj.optBoolean("done", false)
                )
            )
        }
    }

    inner class TaskAdapter : RecyclerView.Adapter<TaskAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val glass = view as LiquidGlassView
            val title: TextView = view.findViewById(R.id.taskTitle)
            val meta: TextView = view.findViewById(R.id.taskMeta)
            val check: CheckBox = view.findViewById(R.id.taskCheck)
            val dot: View = view.findViewById(R.id.categoryDot)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_task, parent, false)
            (view as LiquidGlassView).enableDynamicBackground = true
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val task = tasks[position]

            holder.title.text = task.title
            if (task.done) {
                holder.title.paintFlags = holder.title.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                holder.title.setTextColor(Color.parseColor("#777788"))
            } else {
                holder.title.paintFlags = holder.title.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                holder.title.setTextColor(Color.WHITE)
            }

            val color = Color.parseColor(categoryColors[task.category] ?: "#90A4AE")
            holder.dot.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }

            val dueText = task.dueDate?.let {
                val sdf = SimpleDateFormat("MMM d.", Locale("hu"))
                val dateStr = sdf.format(Date(it))
                val now = System.currentTimeMillis()
                when {
                    it < now && !task.done -> "Lejárt • $dateStr"
                    else -> dateStr
                }
            } ?: "Nincs határidő"

            holder.meta.text = "${task.category}  •  $dueText"
            if (task.dueDate != null && task.dueDate!! < System.currentTimeMillis() && !task.done) {
                holder.meta.setTextColor(Color.parseColor("#FF8A80"))
            } else {
                holder.meta.setTextColor(Color.parseColor("#A0A0B0"))
            }

            holder.check.setOnCheckedChangeListener(null)
            holder.check.isChecked = task.done
            holder.check.setOnCheckedChangeListener { _, isChecked ->
                task.done = isChecked
                saveTasks()
                notifyItemChanged(position)
            }

            holder.itemView.setOnClickListener {
                showTaskDialog(task)
            }
        }

        override fun getItemCount() = tasks.size
    }
}
