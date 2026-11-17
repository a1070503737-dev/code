package com.zhuangxiu.ledger.ui.edit

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhuangxiu.ledger.data.db.CategoryEntity
import com.zhuangxiu.ledger.data.db.CategoryKind
import com.zhuangxiu.ledger.data.db.ExpenseEntity
import com.zhuangxiu.ledger.data.db.PaymentStatus
import com.zhuangxiu.ledger.data.repository.CategoryRepository
import com.zhuangxiu.ledger.data.repository.ExpenseRepository
import com.zhuangxiu.ledger.data.repository.ImageStore
import com.zhuangxiu.ledger.notification.ReminderChecker
import com.zhuangxiu.ledger.util.Dates
import com.zhuangxiu.ledger.util.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** 表单里的一张凭证：id 为 null 表示本次新添加、还没写入数据库 */
data class FormImage(val id: Long?, val fileName: String)

/** 记账 / 编辑表单的状态 */
data class EditUiState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    /** 总价输入框的文本（元） */
    val amountText: String = "",
    /** 已付金额输入框的文本（元），仅新建且状态为定金/待付尾款时使用 */
    val paidText: String = "",
    /** 编辑已有账单时，现有付款记录的合计 */
    val existingPaidCents: Long = 0,
    val date: Long = Dates.today(),
    val stages: List<CategoryEntity> = emptyList(),
    val types: List<CategoryEntity> = emptyList(),
    val stageId: Long? = null,
    val typeId: Long? = null,
    val payee: String = "",
    val note: String = "",
    val status: PaymentStatus = PaymentStatus.PAID,
    val dueDate: Long? = null,
    val images: List<FormImage> = emptyList(),
    val saving: Boolean = false,
    /** 保存成功后置为 true，界面据此返回上一页 */
    val saved: Boolean = false,
    /** 需要提示给用户的错误信息 */
    val error: String? = null,
)

@HiltViewModel
class EditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context,
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
    private val imageStore: ImageStore,
    private val reminderChecker: ReminderChecker,
) : ViewModel() {

    private val expenseId: Long = savedStateHandle.get<Long>("id") ?: -1L

    /** “再记一笔”时作为模板的账单 id，-1 表示没有 */
    private val copyFromId: Long = savedStateHandle.get<Long>("copyFrom") ?: -1L

    private val _uiState = MutableStateFlow(EditUiState(isNew = expenseId <= 0))
    val uiState: StateFlow<EditUiState> = _uiState.asStateFlow()

    /** 编辑时被用户移除的已有凭证，保存时才真正删除 */
    private val removedAttachmentIds = ArrayList<Long>()

    init {
        viewModelScope.launch {
            val stages = categoryRepository.list(CategoryKind.STAGE)
            val types = categoryRepository.list(CategoryKind.TYPE)
            val existing = if (expenseId > 0) expenseRepository.get(expenseId) else null
            val template = if (existing == null && copyFromId > 0) expenseRepository.get(copyFromId)?.expense else null
            _uiState.update { state ->
                if (existing == null) {
                    // 新建：有模板时沿用模板的阶段、类型、商家（金额、日期、凭证都重新填）；
                    // 否则默认选中第一个阶段和类型，方便快速记账
                    state.copy(
                        loading = false,
                        isNew = true,
                        stages = stages,
                        types = types,
                        stageId = template?.stageId?.takeIf { id -> stages.any { it.id == id } } ?: stages.firstOrNull()?.id,
                        typeId = template?.typeId?.takeIf { id -> types.any { it.id == id } } ?: types.firstOrNull()?.id,
                        payee = template?.payee.orEmpty(),
                    )
                } else {
                    val e = existing.expense
                    state.copy(
                        loading = false,
                        isNew = false,
                        amountText = Money.toInput(e.totalCents),
                        existingPaidCents = existing.paidCents,
                        date = e.date,
                        stages = stages,
                        types = types,
                        stageId = e.stageId,
                        typeId = e.typeId,
                        payee = e.payee,
                        note = e.note,
                        status = e.status,
                        dueDate = e.dueDate,
                        images = existing.attachments.map { FormImage(it.id, it.fileName) },
                    )
                }
            }
        }
    }

    fun setAmount(text: String) = _uiState.update { it.copy(amountText = Money.sanitizeInput(text)) }
    fun setPaid(text: String) = _uiState.update { it.copy(paidText = Money.sanitizeInput(text)) }
    fun setDate(date: Long) = _uiState.update { it.copy(date = date) }
    fun setStage(id: Long) = _uiState.update { it.copy(stageId = id) }
    fun setType(id: Long) = _uiState.update { it.copy(typeId = id) }
    fun setPayee(text: String) = _uiState.update { it.copy(payee = text.take(40)) }
    fun setNote(text: String) = _uiState.update { it.copy(note = text.take(300)) }
    fun setStatus(status: PaymentStatus) = _uiState.update { it.copy(status = status) }
    fun setDueDate(date: Long?) = _uiState.update { it.copy(dueDate = date) }
    fun clearError() = _uiState.update { it.copy(error = null) }

    /** 拍照时使用的输出文件 */
    fun newImageFile(): File = imageStore.newFile()

    fun imageFile(fileName: String): File = imageStore.file(fileName)

    /** 拍照完成 */
    fun onPhotoTaken(file: File) = _uiState.update { it.copy(images = it.images + FormImage(null, file.name)) }

    /** 从相册选图：复制到 App 私有目录 */
    fun onImagesPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val names = withContext(Dispatchers.IO) {
                uris.mapNotNull { imageStore.importFromUri(context.contentResolver, it) }
            }
            _uiState.update {
                it.copy(
                    images = it.images + names.map { name -> FormImage(null, name) },
                    error = if (names.size < uris.size) "有图片读取失败" else it.error,
                )
            }
        }
    }

    fun removeImage(image: FormImage) {
        if (image.id == null) imageStore.delete(image.fileName) else removedAttachmentIds += image.id
        _uiState.update { it.copy(images = it.images - image) }
    }

    fun save() {
        val state = _uiState.value
        if (state.saving || state.saved) return
        val total = Money.parseToCents(state.amountText)
        val stageId = state.stageId
        val typeId = state.typeId
        val needsPaidInput = state.isNew &&
            (state.status == PaymentStatus.DEPOSIT || state.status == PaymentStatus.BALANCE_DUE)
        val paid = if (needsPaidInput) Money.parseToCents(state.paidText) else 0L
        val error = when {
            total == null || total <= 0 -> "请输入有效金额（大于 0，整数最多 9 位）"
            stageId == null || typeId == null -> "请选择装修阶段和费用类型"
            needsPaidInput && (paid == null || paid <= 0) -> "请输入有效的已付金额（整数最多 9 位）"
            needsPaidInput && paid!! >= total -> "已付金额应小于总价；如已付清请选择“已付”"
            else -> null
        }
        if (error != null) {
            _uiState.update { it.copy(error = error) }
            return
        }
        _uiState.update { it.copy(saving = true) }
        viewModelScope.launch {
            val draft = ExpenseEntity(
                id = if (state.isNew) 0 else expenseId,
                totalCents = total!!,
                date = state.date,
                stageId = stageId!!,
                typeId = typeId!!,
                payee = state.payee.trim(),
                note = state.note.trim(),
                status = state.status,
                dueDate = state.dueDate,
            )
            val newFiles = state.images.filter { it.id == null }.map { it.fileName }
            try {
                if (state.isNew) {
                    expenseRepository.create(draft, paid ?: 0, newFiles)
                } else {
                    expenseRepository.update(draft, Dates.today(), newFiles, removedAttachmentIds)
                }
                _uiState.update { it.copy(saving = false, saved = true) }
                // 记完账立即检查预算是否需要预警
                runCatching { reminderChecker.checkBudgets() }
            } catch (e: Exception) {
                _uiState.update { it.copy(saving = false, error = "保存失败：${e.message}") }
            }
        }
    }

    /** 没保存就离开时，清理本次新添加但未入库的图片文件 */
    override fun onCleared() {
        val state = _uiState.value
        if (!state.saved) {
            state.images.filter { it.id == null }.forEach { imageStore.delete(it.fileName) }
        }
    }
}
