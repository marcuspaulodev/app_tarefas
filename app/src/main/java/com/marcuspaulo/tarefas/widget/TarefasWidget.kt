package com.marcuspaulo.tarefas.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.marcuspaulo.tarefas.MainActivity
import com.marcuspaulo.tarefas.data.AppDatabase
import com.marcuspaulo.tarefas.data.Task
import com.marcuspaulo.tarefas.data.TaskRepository
import com.marcuspaulo.tarefas.ui.theme.priorityColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DATE_FMT = DateTimeFormatter.ofPattern("dd/MM")

class TarefasWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val database = AppDatabase.getInstance(context)
        val repository = TaskRepository(database.taskDao(), database.tagDao())

        provideContent {
            val today = LocalDate.now()
            val tasks by remember(today) { repository.pendingTasksUpTo(today) }
                .collectAsState(initial = null)

            GlanceTheme {
                WidgetContent(tasks = tasks, today = today)
            }
        }
    }
}

@Composable
private fun WidgetContent(tasks: List<Task>?, today: LocalDate) {
    val openApp = actionStartActivity<MainActivity>()

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(16.dp)
            .padding(12.dp)
    ) {
        val header = when {
            tasks == null -> "Hoje"
            tasks.size == 1 -> "Hoje · 1 pendente"
            else -> "Hoje · ${tasks.size} pendentes"
        }
        Text(
            text = header,
            style = TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = GlanceTheme.colors.onSurface
            ),
            modifier = GlanceModifier.fillMaxWidth().clickable(openApp)
        )
        Spacer(modifier = GlanceModifier.height(8.dp))

        when {
            tasks == null -> Unit
            tasks.isEmpty() -> Text(
                text = "Nada pendente hoje 🎉",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
                modifier = GlanceModifier.fillMaxWidth().clickable(openApp)
            )
            else -> LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(tasks, itemId = { it.id }) { task ->
                    TaskRow(task = task, today = today)
                }
            }
        }
    }
}

@Composable
private fun TaskRow(task: Task, today: LocalDate) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp)
    ) {
        CheckBox(
            checked = false,
            onCheckedChange = actionRunCallback<ToggleTaskAction>(
                actionParametersOf(ToggleTaskAction.TaskIdKey to task.id)
            )
        )
        Box(
            modifier = GlanceModifier
                .width(4.dp)
                .height(20.dp)
                .cornerRadius(2.dp)
                .background(ColorProvider(priorityColor(task.priority)))
        ) {}
        Spacer(modifier = GlanceModifier.width(8.dp))
        Column(
            modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity<MainActivity>())
        ) {
            Text(
                text = task.title,
                maxLines = 1,
                style = TextStyle(fontSize = 14.sp, color = GlanceTheme.colors.onSurface)
            )
            // Cobre tanto as já empurradas pelo rollover quanto as que ainda não foram (app fechado).
            if (task.originalDate.isBefore(today)) {
                Text(
                    text = "Atrasada desde ${task.originalDate.format(DATE_FMT)}",
                    maxLines = 1,
                    style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.error)
                )
            }
        }
    }
}
