package com.lhzkml.jasmine.feature.main.impl

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 把 epoch 毫秒格式化成界面上显示的时间文本。
 *
 * 消息底部的时间小标签、侧边栏会话列表的「最后一条消息时间」共用这一套规则：
 * 当天只给 `HH:mm`；跨天补日期；跨年再补年份 —— 否则历史会话里一堆 `09:15` 谁也分不清是哪天。
 *
 * @return 时间无效（<= 0，例如取不到来源的老数据）时返回 null，调用方据此不显示。
 */
internal fun relativeTimeText(epochMillis: Long): String? {
    if (epochMillis <= 0L) return null
    val zone = ZoneId.systemDefault()
    val at = Instant.ofEpochMilli(epochMillis).atZone(zone)
    val today = LocalDate.now(zone)
    return when {
        at.toLocalDate() == today -> at.format(DateTimeFormatter.ofPattern("HH:mm"))
        at.year == today.year -> at.format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))
        else -> at.format(DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm"))
    }
}
