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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikczemny.prompter.speech.Language
import com.mikczemny.prompter.speech.Languages

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onStart: (script: String, language: Language) -> Unit) {
    var language by remember { mutableStateOf(Languages.DEFAULT) }
    var text by remember { mutableStateOf(Languages.DEFAULT.sample) }
    // Tracks whether the user has hand-edited the script; if not, switching
    // language swaps in that language's sample so the picker is easy to try.
    var edited by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "Prompter", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text(
            text = "Paste your script, pick a language, and press Start. The prompter " +
                "listens and scrolls at your own pace — fully offline once the language " +
                "pack is downloaded.",
            fontSize = 15.sp,
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                value = "${language.displayName}  ·  ${language.englishName}",
                onValueChange = {},
                readOnly = true,
                label = { Text("Language") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                Languages.ALL.forEach { lang ->
                    DropdownMenuItem(
                        text = { Text("${lang.displayName}  ·  ${lang.englishName}  (~${lang.approxMb} MB)") },
                        onClick = {
                            language = lang
                            if (!edited) text = lang.sample
                            expanded = false
                        },
                    )
                }
            }
        }

        SelectionContainer {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    edited = true
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Script") },
                minLines = 8,
            )
        }

        Button(
            onClick = { if (text.isNotBlank()) onStart(text, language) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Start", fontSize = 18.sp)
        }
    }
}
