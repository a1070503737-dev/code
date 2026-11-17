package com.zhuangxiu.ledger.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zhuangxiu.ledger.domain.*
import com.zhuangxiu.ledger.ui.components.*
import com.zhuangxiu.ledger.ui.theme.LedgerTheme
import com.zhuangxiu.ledger.util.Money

@Composable
fun HomeScreen(
    bottomBar: @Composable () -> Unit,
    onAdd: () -> Unit,
    onOpenExpense: (Long) -> Unit,
    onOpenBills: () -> Unit,
    onOpenBudget: () -> Unit,
    onOpenPending: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(state, bottomBar, onAdd, onOpenExpense, onOpenBills, onOpenBudget, onOpenPending)
}

/** 无数据库依赖的首页内容，同时用于界面预览与测试。 */
@Composable
fun HomeContent(
    state: HomeUiState,
    bottomBar: @Composable () -> Unit,
    onAdd: () -> Unit,
    onOpenExpense: (Long) -> Unit,
    onOpenBills: () -> Unit,
    onOpenBudget: () -> Unit,
    onOpenPending: () -> Unit,
) {
    Scaffold(
        bottomBar = bottomBar,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Outlined.Add, null) },
                text = { Text("记一笔", fontWeight = FontWeight.SemiBold) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PageHeading("我家装修", "新家花费，心里有数", Modifier.weight(1f))
                    Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Cottage, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (state.loading) {
                item { LoadingHint() }
            } else {
                item { BudgetOverviewCard(state.summary, state.totalBudget, onOpenBudget) }
                item {
                    BoxWithConstraints {
                        val compact = maxWidth < 340.dp || LocalDensity.current.fontScale > 1.2f
                        val subtitle = if (state.pendingCount == 0) "全部付清，安心装修" else "${state.pendingCount} 笔待结清 · 查看"
                        if (compact) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                PaymentCard("实际已付", state.summary.paidCents, "已登记的付款", false, Modifier.fillMaxWidth())
                                PaymentCard("剩余待付", state.summary.pendingCents, subtitle, true, Modifier.fillMaxWidth(), onOpenPending)
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                PaymentCard("实际已付", state.summary.paidCents, "已登记的付款", false, Modifier.weight(1f))
                                PaymentCard("剩余待付", state.summary.pendingCents, subtitle, true, Modifier.weight(1f), onOpenPending)
                            }
                        }
                    }
                }
                if (state.alerts.isNotEmpty()) item { AlertsCard(state.alerts, onOpenBudget) }
                item {
                    LedgerCard(Modifier.fillMaxWidth()) {
                        SectionTitle("最近账单", Modifier.padding(start = 18.dp, end = 6.dp, top = 8.dp),
                            trailing = { TextButton(onClick = onOpenBills) { Text("查看全部 ›") } })
                        if (state.recent.isEmpty()) {
                            EmptyHint(Icons.AutoMirrored.Outlined.ReceiptLong, "新家的第一笔花费，从这里开始")
                            OutlinedButton(onClick = onAdd, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 24.dp)) {
                                Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("记录第一笔")
                            }
                        }
                        state.recent.forEachIndexed { index, item ->
                            if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 18.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                            ExpenseRow(item, onClick = { onOpenExpense(item.expense.id) }, showDate = true)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetOverviewCard(summary: LedgerSummary, budget: BudgetUsage?, onOpenBudget: () -> Unit) {
    val ink = Color(0xFFF4FAF4)
    val muted = Color(0xFFCBDFD0)
    Card(onClick = onOpenBudget, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF234E3E), contentColor = ink)) {
        Column(Modifier.background(Brush.linearGradient(listOf(Color(0xFF2E6655), Color(0xFF1D4435)))).padding(24.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("装修总花费", style = MaterialTheme.typography.labelLarge, color = muted, modifier = Modifier.weight(1f))
                Text("预算管理 ›", style = MaterialTheme.typography.labelMedium, color = muted)
            }
            Spacer(Modifier.height(12.dp))
            val amount = Money.format(summary.spentCents)
            Text(amount, style = MaterialTheme.typography.displaySmall,
                fontSize = if (amount.length > 13) 26.sp else 36.sp, color = ink)
            Spacer(Modifier.height(4.dp))
            Text("按订单总价统计，包含未付尾款", style = MaterialTheme.typography.bodySmall, color = muted)
            Spacer(Modifier.height(24.dp))
            if (budget == null) {
                HorizontalDivider(color = ink.copy(alpha = 0.18f))
                Spacer(Modifier.height(16.dp))
                Text("给新家定个预算", style = MaterialTheme.typography.titleMedium, color = ink)
                Text("设置总预算，让每一笔花费更有数 ›", style = MaterialTheme.typography.bodySmall, color = muted)
            } else {
                val over = budget.level == BudgetLevel.OVER
                val progressColor = when (budget.level) {
                    BudgetLevel.OVER -> Color(0xFFFFC2B0)
                    BudgetLevel.WARNING -> Color(0xFFFFDA9D)
                    else -> Color(0xFFC7E5AD)
                }
                BoxWithConstraints {
                    if (maxWidth < 280.dp || LocalDensity.current.fontScale > 1.2f) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            BudgetMetric("总预算", budget.budgetCents, muted, ink)
                            BudgetMetric(if (over) "已超支" else "预算剩余", kotlin.math.abs(budget.remainingCents), muted, if (over) progressColor else ink)
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            BudgetMetric("总预算", budget.budgetCents, muted, ink, Modifier.weight(1f))
                            BudgetMetric(if (over) "已超支" else "预算剩余", kotlin.math.abs(budget.remainingCents), muted, if (over) progressColor else ink,
                                Modifier.weight(1f), Alignment.End)
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                LinearProgressIndicator(progress = { budget.fraction }, modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = progressColor, trackColor = ink.copy(alpha = 0.16f), gapSize = 0.dp, drawStopIndicator = {})
                Spacer(Modifier.height(10.dp))
                Text(when (budget.level) {
                    BudgetLevel.OVER -> "已使用 ${budget.percent}% · 已超出预算"
                    BudgetLevel.WARNING -> "已使用 ${budget.percent}% · 留意后续花费"
                    else -> "已使用 ${budget.percent}% · 预算使用正常"
                }, style = MaterialTheme.typography.labelMedium, color = progressColor)
            }
        }
    }
}

@Composable
private fun PaymentCard(title: String, cents: Long, subtitle: String, pending: Boolean,
    modifier: Modifier, onClick: (() -> Unit)? = null) {
    LedgerCard(modifier, onClick = onClick) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (pending) Icons.Outlined.Schedule else Icons.Outlined.TaskAlt, null,
                    Modifier.size(18.dp), tint = if (pending) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(Money.format(cents), style = MaterialTheme.typography.titleLarge,
                fontSize = if (Money.format(cents).length > 11) 16.sp else 20.sp)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AlertsCard(alerts: List<BudgetItem>, onClick: () -> Unit) {
    LedgerCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.WarningAmber, null, Modifier.size(18.dp), tint = LedgerTheme.extra.warning)
                Spacer(Modifier.width(8.dp))
                Text("留意这些预算", style = MaterialTheme.typography.titleMedium)
            }
            alerts.forEach { item ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(item.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(if (item.usage.level == BudgetLevel.OVER) "超支 ${Money.format(-item.usage.remainingCents)}" else "已用 ${item.usage.percent}%",
                        Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = budgetLevelColor(item.usage.level))
                }
            }
        }
    }
}

@Composable
private fun BudgetMetric(label: String, cents: Long, labelColor: Color, valueColor: Color,
    modifier: Modifier = Modifier, alignment: Alignment.Horizontal = Alignment.Start) {
    Column(modifier, horizontalAlignment = alignment) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = labelColor)
        Text(Money.format(cents), style = MaterialTheme.typography.titleMedium, color = valueColor)
    }
}
