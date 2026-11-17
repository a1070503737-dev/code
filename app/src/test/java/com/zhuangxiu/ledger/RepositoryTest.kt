package com.zhuangxiu.ledger

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zhuangxiu.ledger.data.db.AppDatabase
import com.zhuangxiu.ledger.data.db.BudgetEntity
import com.zhuangxiu.ledger.data.db.CategoryEntity
import com.zhuangxiu.ledger.data.db.CategoryKind
import com.zhuangxiu.ledger.data.db.ExpenseEntity
import com.zhuangxiu.ledger.data.db.PaymentStatus
import com.zhuangxiu.ledger.data.repository.BudgetRepository
import com.zhuangxiu.ledger.data.repository.CategoryRepository
import com.zhuangxiu.ledger.data.repository.ExpenseRepository
import com.zhuangxiu.ledger.data.repository.ImageStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Repository 测试：用 Robolectric + Room 内存数据库，在电脑上直接运行，不需要手机或模拟器。
 */
@RunWith(RobolectricTestRunner::class)
// 用普通 Application 代替 LedgerApp，测试里不需要启动 Hilt 和后台任务
@Config(application = Application::class)
class RepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var imageStore: ImageStore
    private lateinit var expenses: ExpenseRepository
    private lateinit var categories: CategoryRepository
    private lateinit var budgets: BudgetRepository

    private lateinit var stages: List<CategoryEntity>
    private lateinit var types: List<CategoryEntity>
    private val today = 20_000L // 任意一个固定日期（epochDay）

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(AppDatabase.SEED_CALLBACK)
            .allowMainThreadQueries()
            .build()
        imageStore = ImageStore(File(context.cacheDir, "test_receipts").apply { deleteRecursively() })
        expenses = ExpenseRepository(db, imageStore)
        categories = CategoryRepository(db)
        budgets = BudgetRepository(db)
        stages = categories.list(CategoryKind.STAGE)
        types = categories.list(CategoryKind.TYPE)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun draft(total: Long, status: PaymentStatus = PaymentStatus.PAID, dueDate: Long? = null) = ExpenseEntity(
        totalCents = total,
        date = today,
        stageId = stages[0].id,
        typeId = types[0].id,
        payee = "老王水电",
        status = status,
        dueDate = dueDate,
    )

    /** 在图片目录里放一个假图片文件，返回文件名 */
    private fun fakeImage(): String = imageStore.importFrom("fake-image".byteInputStream())

    // ---------- 默认分类 ----------

    /** 首次建库自动写入默认分类 */
    @Test
    fun seedsDefaultCategoriesOnFirstCreate() {
        assertEquals(AppDatabase.DEFAULT_STAGES, stages.map { it.name })
        assertEquals(AppDatabase.DEFAULT_TYPES, types.map { it.name })
    }

    // ---------- 账单与付款 ----------

    /** 新建已付账单 自动生成一笔全额付款 */
    @Test
    fun createPaid_addsFullPayment() = runTest {
        val id = expenses.create(draft(1200_00, PaymentStatus.PAID, dueDate = today + 5))
        val saved = expenses.get(id)!!
        assertEquals(PaymentStatus.PAID, saved.expense.status)
        assertEquals(1200_00L, saved.paidCents)
        assertEquals(0L, saved.remainingCents)
        assertNull("已付清的账单不保留到期日", saved.expense.dueDate)
    }

    /** 新建未付账单 没有付款记录 保留到期日 */
    @Test
    fun createUnpaid_noPayment_keepsDueDate() = runTest {
        val id = expenses.create(draft(800_00, PaymentStatus.UNPAID, dueDate = today + 7))
        val saved = expenses.get(id)!!
        assertEquals(PaymentStatus.UNPAID, saved.expense.status)
        assertTrue(saved.payments.isEmpty())
        assertEquals(800_00L, saved.remainingCents)
        assertEquals(today + 7, saved.expense.dueDate)
    }

    /** 新建定金账单 登记总价已付和剩余 */
    @Test
    fun createDeposit_recordsTotalPaidAndRemaining() = runTest {
        val id = expenses.create(draft(10_000_00, PaymentStatus.DEPOSIT), inputPaidCents = 3_000_00)
        val saved = expenses.get(id)!!
        assertEquals(PaymentStatus.DEPOSIT, saved.expense.status)
        assertEquals(3_000_00L, saved.paidCents)
        assertEquals(7_000_00L, saved.remainingCents)
    }

    /** 分多次付款 状态随付款自动变化直到付清 */
    @Test
    fun installments_statusFollowsPaymentsUntilSettled() = runTest {
        val id = expenses.create(draft(10_000_00, PaymentStatus.UNPAID, dueDate = today + 30))

        expenses.addPayment(id, 2_000_00, today + 1)
        assertEquals(PaymentStatus.BALANCE_DUE, expenses.get(id)!!.expense.status)

        expenses.addPayment(id, 3_000_00, today + 2)
        var saved = expenses.get(id)!!
        assertEquals(5_000_00L, saved.paidCents)
        assertEquals(5_000_00L, saved.remainingCents)
        assertEquals(today + 30, saved.expense.dueDate)

        expenses.addPayment(id, 5_000_00, today + 3)
        saved = expenses.get(id)!!
        assertEquals(PaymentStatus.PAID, saved.expense.status)
        assertEquals(3, saved.payments.size)
        assertEquals(0L, saved.remainingCents)
        assertNull(saved.expense.dueDate)
    }

    /** 付款超过剩余金额时按剩余金额记录 */
    @Test
    fun paymentAboveRemainingIsCapped() = runTest {
        val id = expenses.create(draft(1_000_00, PaymentStatus.DEPOSIT), inputPaidCents = 400_00)
        val recorded = expenses.addPayment(id, 9_999_00, today)
        assertEquals(600_00L, recorded)
        assertEquals(1_000_00L, expenses.get(id)!!.paidCents)
        // 已付清后再付款不会产生记录
        assertEquals(0L, expenses.addPayment(id, 100, today))
    }

    /** 删除付款记录后状态回退 */
    @Test
    fun deletingPaymentRevertsStatus() = runTest {
        val id = expenses.create(draft(1_000_00, PaymentStatus.PAID))
        val payment = expenses.get(id)!!.payments.single()
        expenses.deletePayment(payment)
        val saved = expenses.get(id)!!
        assertEquals(PaymentStatus.UNPAID, saved.expense.status)
        assertEquals(1_000_00L, saved.remainingCents)
    }

    /** 编辑时改成已付 自动补一笔结清付款 */
    @Test
    fun updateToPaid_addsSettlingPayment() = runTest {
        val id = expenses.create(draft(5_000_00, PaymentStatus.DEPOSIT, dueDate = today + 3), inputPaidCents = 1_000_00)
        val current = expenses.get(id)!!.expense
        expenses.update(current.copy(status = PaymentStatus.PAID), settleDate = today + 1)
        val saved = expenses.get(id)!!
        assertEquals(PaymentStatus.PAID, saved.expense.status)
        assertEquals(listOf(1_000_00L, 4_000_00L), saved.payments.sortedBy { it.id }.map { it.amountCents })
        assertNull(saved.expense.dueDate)
    }

    /** 列表打开后账单被编辑，结清使用最新总价并保留新备注。重复结清不重复入账。 */
    @Test
    fun settleUsesLatestBillAndIsIdempotent() = runTest {
        val id = expenses.create(draft(1_000_00, PaymentStatus.UNPAID, dueDate = today + 5))
        val old = expenses.get(id)!!.expense
        expenses.update(old.copy(totalCents = 1_500_00, note = "已追加材料"), settleDate = today)
        expenses.addPayment(id, 400_00, today)
        assertEquals(1_100_00L, expenses.settle(id, today + 1))
        assertEquals(0L, expenses.settle(id, today + 1))
        val saved = expenses.get(id)!!
        assertEquals(1_500_00L, saved.expense.totalCents)
        assertEquals("已追加材料", saved.expense.note)
        assertEquals(1_500_00L, saved.paidCents)
        assertEquals(2, saved.payments.size)
        assertEquals(PaymentStatus.PAID, saved.expense.status)
        assertNull(saved.expense.dueDate)
    }

    /** 编辑时调高总价 已付账单变成待付尾款 */
    @Test
    fun updateRaisingTotal_paidBecomesBalanceDue() = runTest {
        val id = expenses.create(draft(1_000_00, PaymentStatus.PAID))
        val current = expenses.get(id)!!.expense
        // 界面上状态仍显示“待付尾款”以外的值时，由仓库按付款记录重新推导
        expenses.update(current.copy(totalCents = 1_500_00, status = PaymentStatus.BALANCE_DUE), settleDate = today)
        val saved = expenses.get(id)!!
        assertEquals(PaymentStatus.BALANCE_DUE, saved.expense.status)
        assertEquals(500_00L, saved.remainingCents)
    }

    /** 编辑时增删凭证 被移除的图片文件也被删除 */
    @Test
    fun updateAttachments_removedImageFileDeleted() = runTest {
        val keep = fakeImage()
        val remove = fakeImage()
        val id = expenses.create(draft(100_00), attachmentFiles = listOf(keep, remove))
        val before = expenses.get(id)!!
        val removedId = before.attachments.first { it.fileName == remove }.id
        val added = fakeImage()

        expenses.update(before.expense, today, addedFiles = listOf(added), removedAttachmentIds = listOf(removedId))

        assertEquals(setOf(keep, added), expenses.get(id)!!.attachments.map { it.fileName }.toSet())
        assertFalse(imageStore.file(remove).exists())
        assertTrue(imageStore.file(keep).exists())
    }

    /** 删除账单 级联删除付款记录和凭证文件 */
    @Test
    fun deleteExpense_cascadesPaymentsAndImageFiles() = runTest {
        val image = fakeImage()
        val id = expenses.create(draft(100_00), attachmentFiles = listOf(image))
        assertTrue(imageStore.file(image).exists())

        expenses.delete(id)

        assertNull(expenses.get(id))
        assertFalse(imageStore.file(image).exists())
        assertTrue(expenses.observeAllAttachments().first().isEmpty())
    }

    /** 账单列表按日期倒序 */
    @Test
    fun expensesOrderedByDateDescending() = runTest {
        expenses.create(draft(1_00).copy(date = today - 2))
        expenses.create(draft(2_00).copy(date = today))
        expenses.create(draft(3_00).copy(date = today - 1))
        assertEquals(listOf(2_00L, 3_00L, 1_00L), expenses.observeAll().first().map { it.expense.totalCents })
    }

    /** 凭证库按添加时间查看全部图片 */
    @Test
    fun galleryListsAllAttachments() = runTest {
        val a = expenses.create(draft(1_00), attachmentFiles = listOf(fakeImage()))
        expenses.create(draft(2_00), attachmentFiles = listOf(fakeImage(), fakeImage()))
        val all = expenses.observeAllAttachments().first()
        assertEquals(3, all.size)
        assertEquals(1, all.count { it.expense.id == a })

        expenses.deleteAttachment(all.first().attachment.id)
        assertEquals(2, expenses.observeAllAttachments().first().size)
        assertFalse(imageStore.file(all.first().attachment.fileName).exists())
    }

    // ---------- 分类 ----------

    /** 新增分类排在最后 重名或空名被拒绝 */
    @Test
    fun addCategory_appendsLast_rejectsDuplicateOrBlank() = runTest {
        assertNotNull(categories.add(CategoryKind.STAGE, " 全屋定制 "))
        assertEquals("全屋定制", categories.list(CategoryKind.STAGE).last().name)
        assertNull(categories.add(CategoryKind.STAGE, "水电"))
        assertNull(categories.add(CategoryKind.STAGE, "   "))
        // 阶段和类型互不影响，可以同名
        assertNotNull(categories.add(CategoryKind.TYPE, "水电"))
    }

    /** 重命名分类 */
    @Test
    fun renameCategory() = runTest {
        assertTrue(categories.rename(stages[0], "方案设计"))
        assertEquals("方案设计", categories.list(CategoryKind.STAGE)[0].name)
        assertFalse(categories.rename(stages[1], "方案设计"))
    }

    /** 上移下移调整排序 */
    @Test
    fun moveCategoryUpAndDown() = runTest {
        // 默认：设计、拆改、水电…… 把“水电”上移两次到最前
        val water = stages[2]
        categories.move(water, -1)
        categories.move(categories.list(CategoryKind.STAGE).first { it.id == water.id }, -1)
        assertEquals(listOf("水电", "设计", "拆改"), categories.list(CategoryKind.STAGE).take(3).map { it.name })
        // 已经在最前，再上移不变
        categories.move(categories.list(CategoryKind.STAGE)[0], -1)
        assertEquals("水电", categories.list(CategoryKind.STAGE)[0].name)
        // 类型的顺序不受影响
        assertEquals(AppDatabase.DEFAULT_TYPES, categories.list(CategoryKind.TYPE).map { it.name })
    }

    /** 有账单在用的分类不能删除 没用过的可以删除并清掉预算 */
    @Test
    fun categoryInUseCannotBeDeleted_unusedDeletesWithBudget() = runTest {
        expenses.create(draft(100_00)) // 用到了 stages[0] 和 types[0]
        assertFalse(categories.delete(stages[0]))
        assertFalse(categories.delete(types[0]))

        budgets.setBudget(stages[1].id, 5_000_00)
        assertTrue(categories.delete(stages[1]))
        assertEquals(stages.size - 1, categories.list(CategoryKind.STAGE).size)
        assertTrue(budgets.listAll().none { it.categoryId == stages[1].id })
    }

    // ---------- 预算 ----------

    /** 设置修改和取消预算 */
    @Test
    fun setUpdateAndClearBudget() = runTest {
        budgets.setBudget(BudgetEntity.TOTAL_ID, 200_000_00)
        budgets.setBudget(stages[0].id, 30_000_00)
        assertEquals(2, budgets.observeAll().first().size)

        budgets.setBudget(BudgetEntity.TOTAL_ID, 250_000_00)
        assertEquals(250_000_00L, budgets.listAll().first { it.categoryId == BudgetEntity.TOTAL_ID }.amountCents)

        budgets.setBudget(stages[0].id, 0)
        assertEquals(listOf(BudgetEntity.TOTAL_ID), budgets.listAll().map { it.categoryId })
    }
}
