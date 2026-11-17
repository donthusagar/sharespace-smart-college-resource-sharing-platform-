package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.AvailabilityStatus
import com.example.data.model.ResourceCategory
import com.example.data.model.ResourceCondition
import com.example.data.model.ResourceListing
import com.example.data.model.ShareMode
import com.example.ui.components.ResourcePhoto
import com.example.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditListingScreen(
    existingListing: ResourceListing?,
    defaultDepartment: String,
    isBusy: Boolean,
    onBack: () -> Unit,
    onSave: (
        title: String,
        description: String,
        category: ResourceCategory,
        condition: ResourceCondition,
        mode: ShareMode,
        availability: AvailabilityStatus,
        maxBorrowDays: Int,
        pickupLocation: String,
        department: String,
        photoUrls: List<String>
    ) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf(existingListing?.title ?: "") }
    var description by remember { mutableStateOf(existingListing?.description ?: "") }
    var category by remember { mutableStateOf(existingListing?.let { ResourceCategory.fromKey(it.category) } ?: ResourceCategory.TEXTBOOKS) }
    var condition by remember { mutableStateOf(existingListing?.let { ResourceCondition.fromKey(it.condition) } ?: ResourceCondition.GOOD) }
    var mode by remember { mutableStateOf(existingListing?.let { ShareMode.fromKey(it.mode) } ?: ShareMode.LEND) }
    var availability by remember { mutableStateOf(existingListing?.let { AvailabilityStatus.fromKey(it.availability) } ?: AvailabilityStatus.AVAILABLE) }
    var maxBorrowDaysText by remember { mutableStateOf((existingListing?.maxBorrowDays ?: 14L).toString()) }
    var pickupLocation by remember { mutableStateOf(existingListing?.pickupLocation ?: "Main Library Quad") }
    var department by remember { mutableStateOf(existingListing?.department ?: defaultDepartment) }
    val photos = remember { mutableStateListOf<String>().apply { existingListing?.photoUrls?.let { addAll(it) } } }

    LaunchedEffect(existingListing?.id) {
        if (existingListing != null && title.isEmpty()) {
            title = existingListing.title
            description = existingListing.description
            category = ResourceCategory.fromKey(existingListing.category)
            condition = ResourceCondition.fromKey(existingListing.condition)
            mode = ShareMode.fromKey(existingListing.mode)
            availability = AvailabilityStatus.fromKey(existingListing.availability)
            maxBorrowDaysText = existingListing.maxBorrowDays.toString()
            pickupLocation = existingListing.pickupLocation
            department = existingListing.department
            photos.clear()
            photos.addAll(existingListing.photoUrls)
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 5)
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val encoded = withContext(Dispatchers.IO) {
                    uris.mapNotNull { ImageUtils.encodeUriToCompactDataUri(context, it) }
                }
                encoded.forEach { dataUri ->
                    if (photos.size < 5) photos.add(dataUri)
                }
            }
        }
    }

    val isValid = title.trim().length >= 2 &&
        description.trim().length >= 5 &&
        pickupLocation.trim().length >= 2 &&
        department.trim().length >= 2 &&
        (maxBorrowDaysText.toIntOrNull() ?: 0) in 1..180

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existingListing == null) "List Campus Resource" else "Edit Listing") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(120) },
                    label = { Text("Resource Title *") },
                    placeholder = { Text("e.g., Linear Algebra 5th Ed or TI-84 Plus") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("listing_title_input")
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(1500) },
                    label = { Text("Description *") },
                    placeholder = { Text("Condition notes, included accessories, course compatibility...") },
                    minLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("listing_description_input")
                )

                Text("Category", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ResourceCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.label) }
                        )
                    }
                }

                Text("Condition", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ResourceCondition.entries.forEach { cond ->
                        FilterChip(
                            selected = condition == cond,
                            onClick = { condition = cond },
                            label = { Text(cond.label) }
                        )
                    }
                }

                Text("Sharing Mode", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ShareMode.entries.forEach { m ->
                        FilterChip(
                            selected = mode == m,
                            onClick = { mode = m },
                            label = { Text(m.label) }
                        )
                    }
                }

                if (existingListing != null) {
                    Text("Availability", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AvailabilityStatus.entries.forEach { avail ->
                            FilterChip(
                                selected = availability == avail,
                                onClick = { availability = avail },
                                label = { Text(avail.label) }
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = maxBorrowDaysText,
                        onValueChange = { maxBorrowDaysText = it.filter(Char::isDigit).take(3) },
                        label = { Text("Max Borrow Days") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = department,
                        onValueChange = { department = it.take(80) },
                        label = { Text("Department *") },
                        singleLine = true,
                        modifier = Modifier.weight(1.4f)
                    )
                }

                OutlinedTextField(
                    value = pickupLocation,
                    onValueChange = { pickupLocation = it.take(160) },
                    label = { Text("Campus Pickup Location *") },
                    placeholder = { Text("e.g., Gates CS Atrium / Science Library") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("listing_pickup_input")
                )

                // Photos (Up to 5 via Android Photo Picker)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Photos (${photos.size}/5)", style = MaterialTheme.typography.titleSmall)
                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        enabled = photos.size < 5
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Pick Photos")
                    }
                }

                if (photos.isNotEmpty()) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        photos.forEachIndexed { index, ref ->
                            Box(
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            ) {
                                ResourcePhoto(
                                    photoRef = ref,
                                    contentDescription = "Photo ${index + 1}",
                                    modifier = Modifier.fillMaxSize()
                                )
                                IconButton(
                                    onClick = { photos.removeAt(index) },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(24.dp)
                                        .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove photo",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        onSave(
                            title,
                            description,
                            category,
                            condition,
                            mode,
                            availability,
                            maxBorrowDaysText.toIntOrNull()?.coerceIn(1, 180) ?: 14,
                            pickupLocation,
                            department,
                            photos.toList()
                        )
                    },
                    enabled = isValid && !isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("save_listing_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(if (existingListing == null) "Publish Resource" else "Save Changes")
                }
            }
        }
    }
}
