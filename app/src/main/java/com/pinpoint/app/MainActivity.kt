package com.taskly.app

import android.app.DatePickerDialog
import android.content.Context
import android.graphics.Color
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
        fabGlass = findViewById(R.id.fabGlass)
        fabGlass.enableDynamicBackground = true

        loadTasks()

        adapter = TaskAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        fabGlass.setOnClickListener { showAddDialog() }
    }

    private fun showAddDialog(editTask: Task? = null) {
        val view = layoutInflater.inflate(android.R.layout.simple_list_item_1, null)
        // Egyszerű dialógus
        val input = EditText(this).apply {
            hint = "Feladat neve"
            setText(editTask?.title ?: "")
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setPadding(40, 30, 40, 30)
        }

        var selectedCategory = editTask?.category ?: "Egyéb"
        var selectedDate: Long? = editTask?.dueDate

        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(if (editTask == null) "Új feladat" else "Szerkesztés")
            .setView(input)
            .setPositiveButton("Mentés") { _, _ ->
                val title = input.text.toString().trim()
                if (title.isNotEmpty()) {
                    if (editTask == null) {
                        tasks.add(0, Task(title = title, category = selectedCategory, dueDate = selectedDate))
                    } else {
                        editTask.title = title
                        editTask.category = selectedCategory
                        editTask.dueDate = selectedDate
                    }
                    saveTasks()
                    adapter.notifyDataSetChanged()
                }
            }
            .setNeutralButton("Dátum") { _, _ ->
                val cal = Calendar.getInstance()
                DatePickerDialog(this, { _, y, m, d ->
                    cal.set(y, m, d)
                    selectedDate = cal.timeInMillis
                    showAddDialog(editTask?.copy(title = input.text.toString(), dueDate = selectedDate, category = selectedCategory)
                        ?: Task(title = input.text.toString(), dueDate = selectedDate, category = selectedCategory))
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
            }
            .setNegativeButton("Mégse", null)
            .show()
    }

    private fun saveTasks() {
        val arr = JSONArray()
        tasks.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id)
                put("title", it.title)
                put("dueDate", it.dueDate ?: JSONObject.NULL)
                put("category", it.category)
                put("done", it.done)
            })
        }
        getSharedPreferences("taskly", Context.MODE_PRIVATE)
            .edit().putString("tasks", arr.toString()).apply()
    }

    private fun loadTasks() {
        val json = getSharedPreferences("taskly", Context.MODE_PRIVATE).getString("tasks", "[]")
        val arr = JSONArray(json)
        tasks.clear()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            tasks.add(Task(
                id = obj.getString("id"),
                title = obj.getString("title"),
                dueDate = if (obj.isNull("dueDate")) null else obj.getLong("dueDate"),
                category = obj.getString("category"),
                done = obj.getBoolean("done")
            ))
        }
    }

    inner class TaskAdapter : RecyclerView.Adapter<TaskAdapter.VH>() {
        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val glass: LiquidGlassView = view as LiquidGlassView
            val title: TextView = view.findViewById(R.id.taskTitle)
            val due: TextView = view.findViewById(R.id.taskDue)
            val check: CheckBox = view.findViewById(R.id.taskCheck)
            val dot: View = view.findViewById(R.id.categoryDot)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_task, parent, false)
            (view as LiquidGlassView).enableDynamicBackground = true
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val task = tasks[position]
            holder.title.text = task.title
            holder.title.paint.isStrikeThruText = task.done

            val color = Color.parseColor(categoryColors[task.category] ?: "#90A4AE")
            val gd = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }
            holder.dot.background = gd

            holder.due.text = task.dueDate?.let {
                val sdf = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())
                "Határidő: ${sdf.format(Date(it))}"
            } ?: "Nincs határidő"

            holder.check.isChecked = task.done
            holder.check.setOnCheckedChangeListener { _, isChecked ->
                task.done = isChecked
                saveTasks()
                notifyItemChanged(position)
            }

            holder.itemView.setOnLongClickListener {
                tasks.removeAt(position)
                saveTasks()
                notifyDataSetChanged()
                true
            }
        }

        override fun getItemCount() = tasks.size
    }
}
