package com.zhuangxiu.ledger.ui.edit

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import coil3.compose.AsyncImage
import com.zhuangxiu.ledger.data.db.CategoryEntity
import com.zhuangxiu.ledger.data.db.PaymentStatus
import com.zhuangxiu.ledger.ui.components.LedgerCard
import com.zhuangxiu.ledger.ui.components.LedgerDatePickerDialog
import com.zhuangxiu.ledger.util.Dates
import com.zhuangxiu.ledger.util.Money

/** 记账 / 编辑页 */
@Composable
fun EditScreen(onBack: () -> Unit, viewModel: EditViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val amountFocus = remember { FocusRequester() }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showDuePicker by rememberSaveable { mutableStateOf(false) }
    var showCamera by rememberSaveable { mutableStateOf(false) }

    // 系统相册选图（无需存储权限）
    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(9)) { uris ->
        viewModel.onImagesPicked(uris)
    }

    LaunchedEffect(state.saved) { if (state.saved) onBack() }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }
    // 新建时自动聚焦金额输入框并弹出数字键盘，记一笔更快
    LaunchedEffect(state.loading) {
        if (!state.loading && state.isNew) runCatching { amountFocus.requestFocus() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "记一笔" else "编辑账单") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回") }
                },
                actions = {
                    TextButton(onClick = viewModel::save, enabled = !state.saving && !state.loading, modifier = Modifier.semantics { contentDescription = "保存账单" }) { Text("保存") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (state.loading) return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ---------- 金额 ----------
            LedgerCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Text(
                        "总价",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "¥",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.width(6.dp))
                        BasicTextField(
                            value = state.amountText,
                            onValueChange = viewModel::setAmount,
                            textStyle = MaterialTheme.typography.displaySmall.copy(
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(amountFocus)
                                .semantics { contentDescription = "账单总金额" },
                            decorationBox = { inner ->
                                Box {
                                    if (state.amountText.isEmpty()) {
                                        Text(
                                            "0.00",
                                            style = MaterialTheme.typography.displaySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f),
                                        )
                                    }
                                    inner()
                                }
                            },
                        )
                    }
                }
            }

            // ---------- 付款状态 ----------
            FormSection("付款状态") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PaymentStatus.entries.forEach { status ->
                        FilterChip(
                            selected = state.status == status,
                            onClick = { viewModel.setStatus(status) },
                            label = { Text(status.label) },
                        )
                    }
                }
                val partial = state.status == PaymentStatus.DEPOSIT || state.status == PaymentStatus.BALANCE_DUE
                if (state.isNew && partial) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.paidText,
                        onValueChange = viewModel::setPaid,
                        label = { Text(if (state.status == PaymentStatus.DEPOSIT) "已付定金" else "已付金额") },
                        prefix = { Text("¥") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        supportingText = {
                            val total = Money.parseToCents(state.amountText)
                            val paid = Money.parseToCents(state.paidText)
                            if (total != null && paid != null && paid < total) {
                                Text("剩余待付 ${Money.format(total - paid)}")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (!state.isNew) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (state.status == PaymentStatus.PAID) {
                            "已付 ${Money.format(state.existingPaidCents)}；保存为“已付”会自动补记一笔结清付款"
                        } else {
                            "已付 ${Money.format(state.existingPaidCents)}；分期付款请在账单详情页登记，状态会随付款记录自动更新"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.status != PaymentStatus.PAID) {
                    Spacer(Modifier.height(8.dp))
                    PickerRow(
                        icon = Icons.Outlined.Event,
                        label = "待付到期日",
                        value = state.dueDate?.let(Dates::format) ?: "不设置",
                        onClick = { showDuePicker = true },
                        onClear = if (state.dueDate != null) ({ viewModel.setDueDate(null) }) else null,
                    )
                }
            }

            // ---------- 日期 ----------
            PickerRow(
                icon = Icons.Outlined.CalendarToday,
                label = "日期",
                value = Dates.format(state.date),
                onClick = { showDatePicker = true },
            )

            // ---------- 分类 ----------
            FormSection("装修阶段") { CategoryChips(state.stages, state.stageId, viewModel::setStage) }
            FormSection("费用类型") { CategoryChips(state.types, state.typeId, viewModel::setType) }

            // ---------- 商家 / 备注 ----------
            OutlinedTextField(
                value = state.payee,
                onValueChange = viewModel::setPayee,
                label = { Text("商家 / 工人") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::setNote,
                label = { Text("备注") },
                minLines = 2,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth(),
            )

            // ---------- 凭证 ----------
            FormSection("凭证（小票、合同照片）") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    state.images.forEach { image ->
                        Box(Modifier.size(84.dp)) {
                            AsyncImage(
                                model = viewModel.imageFile(image.fileName),
                                contentDescription = "凭证",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(12.dp)),
                            )
                            // 右上角删除按钮
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(22.dp)
                                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f), CircleShape)
                                    .clickable { viewModel.removeImage(image) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = "移除",
                                    tint = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                    AddImageTile(Icons.Outlined.PhotoCamera, "拍照") { showCamera = true }
                    AddImageTile(Icons.Outlined.PhotoLibrary, "相册") {
                        pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                }
            }

            Button(
                onClick = viewModel::save,
                enabled = !state.saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) { Text(if (state.saving) "保存中…" else "保存") }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showDatePicker) {
        LedgerDatePickerDialog(
            initialEpochDay = state.date,
            onPick = {
                viewModel.setDate(it)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }
    if (showDuePicker) {
        LedgerDatePickerDialog(
            initialEpochDay = state.dueDate,
            onPick = {
                viewModel.setDueDate(it)
                showDuePicker = false
            },
            onDismiss = { showDuePicker = false },
        )
    }
    if (showCamera) {
        CameraCaptureDialog(
            createFile = viewModel::newImageFile,
            onCaptured = {
                viewModel.onPhotoTaken(it)
                showCamera = false
            },
            onDismiss = { showCamera = false },
        )
    }
}

@Composable
private fun FormSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        content()
    }
}

/** 分类选择：一排可换行的标签 */
@Composable
private fun CategoryChips(categories: List<CategoryEntity>, selectedId: Long?, onSelect: (Long) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        categories.forEach { category ->
            FilterChip(
                selected = category.id == selectedId,
                onClick = { onSelect(category.id) },
                label = { Text(category.name) },
            )
        }
    }
}

/** 点击后弹出选择器的一行（日期等） */
@Composable
private fun PickerRow(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit,
    onClear: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (onClear != null) {
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Outlined.Close,
                contentDescription = "清除",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(18.dp)
                    .clickable(onClick = onClear),
            )
        }
    }
}

/** “拍照 / 相册”添加凭证的方块按钮 */
@Composable
private fun AddImageTile(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier
            .size(84.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
