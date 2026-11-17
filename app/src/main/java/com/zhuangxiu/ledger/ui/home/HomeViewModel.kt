package com.zhuangxiu.ledger.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhuangxiu.ledger.data.db.BudgetEntity
import com.zhuangxiu.ledger.data.db.ExpenseWithDetails
import com.zhuangxiu.ledger.data.repository.BudgetRepository
import com.zhuangxiu.ledger.data.repository.CategoryRepository
import com.zhuangxiu.ledger.data.repository.ExpenseRepository
import com.zhuangxiu.ledger.domain.BudgetCalculator
import com.zhuangxiu.ledger.domain.BudgetItem
import com.zhuangxiu.ledger.domain.BudgetLevel
import com.zhuangxiu.ledger.domain.BudgetUsage
import com.zhuangxiu.ledger.domain.LedgerSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class HomeUiState(
    val loading: Boolean = true,
    val summary: LedgerSummary = LedgerSummary(0, 0, 0),
    /** 总预算使用情况；没设置总预算时为 null */
    val totalBudget: BudgetUsage? = null,
    /** 需要预警或已超支的分类预算 */
    val alerts: List<BudgetItem> = emptyList(),
    /** 还有余款未付的账单数量 */
    val pendingCount: Int = 0,
    /** 最近的几笔账单 */
    val recent: List<ExpenseWithDetails> = emptyList(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    expenseRepository: ExpenseRepository,
    budgetRepository: BudgetRepository,
    categoryRepository: CategoryRepository,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        expenseRepository.observeAll(),
        budgetRepository.observeAll(),
        categoryRepository.observeAll(),
    ) { expenses, budgets, categories ->
        val items = BudgetCalculator.budgetItems(budgets, categories, expenses)
        HomeUiState(
            loading = false,
            summary = BudgetCalculator.summary(expenses),
            totalBudget = items.firstOrNull { it.categoryId == BudgetEntity.TOTAL_ID }?.usage,
            alerts = items.filter {
                it.categoryId != BudgetEntity.TOTAL_ID &&
                    (it.usage.level == BudgetLevel.WARNING || it.usage.level == BudgetLevel.OVER)
            },
            pendingCount = expenses.count { it.remainingCents > 0 },
            recent = expenses.take(6),
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
