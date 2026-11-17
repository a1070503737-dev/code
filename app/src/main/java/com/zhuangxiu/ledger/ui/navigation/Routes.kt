package com.zhuangxiu.ledger.ui.navigation

/** 全部页面路由 */
object Routes {
    // 底部导航的 4 个页面
    const val HOME = "home"
    const val BILLS = "bills"
    const val STATS = "stats"
    const val MINE = "mine"

    // 记账 / 编辑页：id 为 -1 表示新建；copyFrom 为某笔账单 id 时，新建并预填它的阶段、类型、商家
    const val EDIT = "edit?id={id}&copyFrom={copyFrom}"
    fun edit(id: Long = -1, copyFrom: Long = -1) = "edit?id=$id&copyFrom=$copyFrom"

    // 账单详情页
    const val DETAIL = "detail/{id}"
    fun detail(id: Long) = "detail/$id"

    // 图片查看页：expenseId 为 -1 表示查看凭证库里的全部图片
    const val VIEWER = "viewer?expenseId={expenseId}&index={index}"
    fun viewer(expenseId: Long, index: Int) = "viewer?expenseId=$expenseId&index=$index"

    // “我的”里的子页面
    const val GALLERY = "gallery"
    const val BUDGET = "budget"
    const val CATEGORIES = "categories"
    const val BACKUP = "backup"
    const val REMINDER = "reminder"
    const val UPDATE = "update"
}
