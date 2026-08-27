package com.questlog.data.local.converter

import androidx.room.TypeConverter
import com.questlog.data.local.entity.ItemType

class ItemTypeConverter {
    @TypeConverter
    fun fromItemType(value: ItemType): String = value.name
    @TypeConverter
    fun toItemType(value: String): ItemType = ItemType.valueOf(value)
}
