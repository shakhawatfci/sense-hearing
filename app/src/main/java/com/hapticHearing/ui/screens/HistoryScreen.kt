package com.hapticHearing.ui.screens

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hapticHearing.R
import com.hapticHearing.data.db.EventLog
import com.hapticHearing.data.db.EventLogDao
import com.hapticHearing.ui.components.HSpace
import com.hapticHearing.ui.components.SoundIcons
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class HistoryViewModel @Inject constructor(private val dao: EventLogDao) : ViewModel() {
    val events: StateFlow<List<EventLog>> = dao.observeRecent().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun clear() = viewModelScope.launch { dao.clear() }
}

@Composable
fun HistoryScreen(vm: HistoryViewModel = hiltViewModel()) {
    val events by vm.events.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.nav_history), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            if (events.isNotEmpty()) {
                OutlinedButton(onClick = vm::clear, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.history_clear)) }
            }
        }
        if (events.isEmpty()) {
            Text(stringResource(R.string.history_empty), style = MaterialTheme.typography.bodyLarge)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(events, key = { it.id }) { e -> HistoryRow(e) }
        }
    }
}

@Composable
private fun HistoryRow(e: EventLog) {
    val name = stringResource(SoundIcons.name(e.label))
    val time = DateUtils.getRelativeTimeSpanString(e.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
    Card(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(SoundIcons.icon(e.label), contentDescription = null, modifier = Modifier.size(48.dp))
            HSpace(16)
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "$time · ≈ ${e.levelDb.roundToInt()} dB · ${(e.confidence * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
