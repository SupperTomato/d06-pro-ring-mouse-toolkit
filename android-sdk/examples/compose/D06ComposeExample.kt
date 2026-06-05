package com.example.d06

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.d06.sdk.core.D06Event

@Composable
fun D06EventPanel(events: List<D06Event>) {
    Column {
        Text("D06 events")
        LazyColumn {
            items(events) { event ->
                Text(event.toString())
            }
        }
    }
}
