package com.zhuangxiu.ledger.data.repository

import androidx.room.withTransaction
import com.zhuangxiu.ledger.data.db.AppDatabase
import com.zhuangxiu.ledger.data.db.AttachmentEntity
import com.zhuangxiu.ledger.data.db.AttachmentWithExpense
import com.zhuangxiu.ledger.data.db.ExpenseEntity
import com.zhuangxiu.ledger.data.db.ExpenseWithDetails
import com.zhuangxiu.ledger.data.db.PaymentEntity
import com.zhuangxiu.ledger.data.db.PaymentStatus
import com.zhuangxiu.ledger.domain.PaymentLogic
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 账单仓库：账单、付款记录、凭证的增删改查。
 * 付款状态始终由“总价 + 付款记录”推导，保证数据一致。
 */
@Singleton
class ExpenseRepository @Inject constructor(
    private val db: AppDatabase,
    private val imageStore: ImageStore,
) {
    private val dao = db.expenseDao()

    fun observeAll(): Flow<List<ExpenseWithDetails>> = dao.observeAll()

    fun observe(id: Long): Flow<ExpenseWithDetails?> = dao.observeById(id)

    fun observeAllAttachments(): Flow<List<AttachmentWithExpense>> = dao.observeAllAttachments()

    suspend fun get(id: Long): ExpenseWithDetails? = dao.getById(id)

    suspend fun listAll(): List<ExpenseWithDetails> = dao.listAll()

    /**
     * 新建账单。
     * @param draft 用户填写的账单，其中 status 是用户选择的状态
     * @param inputPaidCents 选择“定金/待付尾款”时填写的已付金额
     * @param attachmentFiles 已保存到私有目录的凭证文件名
     * @return 新账单的 id
     */
    suspend fun create(
        draft: ExpenseEntity,
        inputPaidCents: Long = 0,
        attachmentFiles: List<String> = emptyList(),
    ): Long = db.withTransaction {
        val paid = PaymentLogic.initialPaid(draft.status, draft.totalCents, inputPaidCents)
        val status = PaymentLogic.resolveStatus(draft.status, draft.totalCents, paid)
        val id = dao.insertExpense(
            draft.copy(
                id = 0,
                status = status,
                // 已付清的账单不需要到期日
                dueDate = if (status == PaymentStatus.PAID) null else draft.dueDate,
            ),
        )
        if (paid > 0) {
            val note = if (status == PaymentStatus.DEPOSIT) "定金" else ""
            dao.insertPayment(PaymentEntity(expenseId = id, amountCents = paid, date = draft.date, note = note))
        }
        attachmentFiles.forEach { dao.insertAttachment(AttachmentEntity(expenseId = id, fileName = it)) }
        id
    }

    /**
     * 修改账单。
     * - 用户把状态改成“已付”而付款记录不足时，自动补一笔结清付款
     * - 其他情况按现有付款记录重新推导状态
     * @param settleDate 自动结清付款的日期（epochDay）
     */
    suspend fun update(
        expense: ExpenseEntity,
        settleDate: Long,
        addedFiles: List<String> = emptyList(),
        removedAttachmentIds: List<Long> = emptyList(),
    ) {
        val removedFiles = ArrayList<String>()
        db.withTransaction {
            val current = dao.getById(expense.id) ?: return@withTransaction
            var paid = current.paidCents
            if (expense.status == PaymentStatus.PAID && paid < expense.totalCents) {
                dao.insertPayment(
                    PaymentEntity(
                        expenseId = expense.id,
                        amountCents = expense.totalCents - paid,
                        date = settleDate,
                        note = "结清",
                    ),
                )
                paid = expense.totalCents
            }
            val status = PaymentLogic.resolveStatus(expense.status, expense.totalCents, paid)
            dao.updateExpense(
                expense.copy(
                    status = status,
                    dueDate = if (status == PaymentStatus.PAID) null else expense.dueDate,
                    createdAt = current.expense.createdAt,
                ),
            )
            addedFiles.forEach { dao.insertAttachment(AttachmentEntity(expenseId = expense.id, fileName = it)) }
            removedAttachmentIds.forEach { attachmentId ->
                dao.getAttachment(attachmentId)?.let { removedFiles += it.fileName }
                dao.deleteAttachment(attachmentId)
            }
        }
        // 事务成功后再删文件，避免数据库回滚后图片丢失
        removedFiles.forEach(imageStore::delete)
    }

    /** 删除账单，同时删除它的付款记录（级联）和凭证图片文件 */
    suspend fun delete(id: Long) {
        val files = dao.getById(id)?.attachments?.map { it.fileName }.orEmpty()
        dao.deleteExpense(id)
        files.forEach(imageStore::delete)
    }

    /**
     * 登记一次付款（分期付款），并自动更新付款状态。
     * 付款金额超过剩余金额时按剩余金额记录。
     * @return 实际登记的金额；账单不存在或已付清时返回 0
     */
    suspend fun addPayment(expenseId: Long, amountCents: Long, date: Long, note: String = ""): Long =
        db.withTransaction {
            val current = dao.getById(expenseId) ?: return@withTransaction 0L
            val amount = amountCents.coerceAtMost(current.remainingCents)
            if (amount <= 0) return@withTransaction 0L
            dao.insertPayment(PaymentEntity(expenseId = expenseId, amountCents = amount, date = date, note = note))
            refreshStatus(expenseId)
            amount
        }

    /** 按事务内最新余额结清，不把列表中的旧账单字段写回数据库。 */
    suspend fun settle(expenseId: Long, date: Long): Long =
        addPayment(expenseId, Long.MAX_VALUE, date, note = "结清")

    /** 删除一条付款记录，并重新推导付款状态 */
    suspend fun deletePayment(payment: PaymentEntity) = db.withTransaction {
        dao.deletePayment(payment.id)
        refreshStatus(payment.expenseId)
    }

    /** 删除一张凭证（数据库记录 + 图片文件） */
    suspend fun deleteAttachment(attachmentId: Long) {
        val attachment = dao.getAttachment(attachmentId) ?: return
        dao.deleteAttachment(attachmentId)
        imageStore.delete(attachment.fileName)
    }

    /** 付款记录变化后，按最新的已付金额更新账单状态 */
    private suspend fun refreshStatus(expenseId: Long) {
        val current = dao.getById(expenseId) ?: return
        val status = PaymentLogic.resolveStatus(current.expense.status, current.expense.totalCents, current.paidCents)
        dao.updateExpense(
            current.expense.copy(
                status = status,
                dueDate = if (status == PaymentStatus.PAID) null else current.expense.dueDate,
            ),
        )
    }
}
