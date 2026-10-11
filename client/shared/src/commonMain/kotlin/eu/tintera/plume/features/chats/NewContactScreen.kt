package eu.tintera.plume.features.chats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.ui.CountryPickerSheet
import eu.tintera.plume.core.ui.PhoneField
import eu.tintera.plume.core.ui.reject
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.arrow_back_24dp
import eu.tintera.plume.resources.common_back
import eu.tintera.plume.resources.edit_profile_first_name
import eu.tintera.plume.resources.edit_profile_last_name
import eu.tintera.plume.resources.new_chat_new_contact
import eu.tintera.plume.resources.new_contact_add
import eu.tintera.plume.resources.new_contact_help
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Formulář nového kontaktu: jméno, příjmení a číslo. Tvarem jako založení kanálu (akce
 * v liště, pole pod sebou), číslo stejným blokem jako u přihlášení ([PhoneField]) — zadává se
 * tu totéž a uživatel ho tak pozná.
 */
@Composable
fun NewContactScreen(
    viewModel: NewContactViewModel,
    onBack: () -> Unit,
    onAdded: (chatId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val countries by viewModel.countries.collectAsStateWithLifecycle()
    var pickingCountry by rememberSaveable { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val currentOnAdded by rememberUpdatedState(onAdded)
    val error = viewModel.error

    LaunchedEffect(viewModel.added) {
        viewModel.added?.let { currentOnAdded(it) }
    }
    // Chyba přišla po ťuknutí na Přidat — odezva patří k akci uživatele.
    LaunchedEffect(error) {
        if (error != null) haptics.reject()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.new_chat_new_contact)) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !viewModel.adding, shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(Res.drawable.arrow_back_24dp), contentDescription = stringResource(Res.string.common_back))
                    }
                },
                actions = {
                    if (viewModel.adding) {
                        LoadingIndicator(Modifier.padding(end = 12.dp).size(40.dp))
                    } else {
                        TextButton(onClick = viewModel::add, enabled = viewModel.canAdd) {
                            Text(stringResource(Res.string.new_contact_add))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = viewModel.firstName,
                onValueChange = viewModel::onFirstNameChange,
                label = { Text(stringResource(Res.string.edit_profile_first_name)) },
                singleLine = true,
                enabled = !viewModel.adding,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = viewModel.lastName,
                onValueChange = viewModel::onLastNameChange,
                label = { Text(stringResource(Res.string.edit_profile_last_name)) },
                singleLine = true,
                enabled = !viewModel.adding,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            PhoneField(
                phone = viewModel.phone,
                isError = error != null,
                onPhoneChange = viewModel::onPhoneChange,
                onPickCountry = { pickingCountry = true },
                onSubmit = viewModel::add,
            )
            if (error != null) {
                Text(
                    text = error.resolve(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            // Kam kontakt jde a co se o nás protějšek dozví — u „přidat kontakt" se to čeká
            // od telefonu, ne od Telegramu.
            Text(
                text = stringResource(Res.string.new_contact_help),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }

    if (pickingCountry) {
        CountryPickerSheet(
            countries = countries,
            selectedCodes = setOfNotNull(viewModel.phone.country?.code),
            onPick = {
                viewModel.selectCountry(it)
                pickingCountry = false
            },
            onDismiss = { pickingCountry = false },
        )
    }
}
