package ksqlite

import cnames.structs.sqlite3_value
import kotlinx.cinterop.*
import sqlite3.*

class SQLiteValue(val ptr: CPointer<sqlite3_value>) {
	fun asString() : String? = sqlite3_value_text(ptr)?.toKString()
	fun asLong() : Long = sqlite3_value_int64(ptr)
	fun asInt() : Int = sqlite3_value_int(ptr)
	fun asDouble() : Double = sqlite3_value_double(ptr)
	fun asBlob() : ByteArray? {
		val blob = sqlite3_value_blob(ptr)
		return when {
			blob != null -> blob.readBytes(sqlite3_value_bytes(ptr))
			sqlite3_value_type(ptr) != SQLITE_NULL -> byteArrayOf()
			else -> null
		}
	}
	fun asPointer(key: String) : Any? = sqlite3_value_pointer(ptr, key)?.let { it.asStableRef<Any>().get() }
}