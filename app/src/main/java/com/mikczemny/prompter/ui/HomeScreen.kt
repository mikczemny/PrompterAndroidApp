package com.mikczemny.prompter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val SAMPLE_SCRIPT =
    "Cześć, witam Was serdecznie w kolejnym odcinku. Dzisiaj opowiem o tym, " +
        "jak działa prompter sterowany głosem. Zaczynam mówić, a tekst sam " +
        "przewija się w moim tempie. Mogę zwolnić, mogę przyspieszyć, a nawet " +
        "zrobić krótką pauzę, a prompter poczeka i ruszy dalej, gdy tylko " +
        "podejmę mówienie."

@Composable
fun HomeScreen(onStart: (String) -> Unit) {
    var text by remember { mutableStateOf(SAMPLE_SCRIPT) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Prompter",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Wklej lub wpisz swój scenariusz. Prompter będzie śledził Twój " +
                "głos i przewijał tekst w Twoim tempie — całkowicie offline.",
            fontSize = 15.sp,
        )

        SelectionContainer {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                label = { Text("Scenariusz") },
                minLines = 8,
            )
        }

        Button(
            onClick = { if (text.isNotBlank()) onStart(text) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Text("Start", fontSize = 18.sp)
        }
    }
}
