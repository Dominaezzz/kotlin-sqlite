package ksqlite

import kotlinx.cinterop.*
import sqlite3.*

class SQLiteValues internal constructor(private var values: CPointer<CPointerVar<sqlite3_value>>, var count: Int) {
	private fun checkValueIndex(valueIndex: Int) {
		check(valueIndex in 0 until count) { "Value index out of bounds." }
	}

	fun getAsString(valueIndex: Int) : String? {
		checkValueIndex(valueIndex)

		return sqlite3_value_text(values[valueIndex])?.toKString()
	}

	fun getAsLong(valueIndex: Int) : Long {
		checkValueIndex(valueIndex)

		return sqlite3_value_int64(values[valueIndex])
	}

	fun getAsInt(valueIndex: Int) : Int {
		checkValueIndex(valueIndex)

		return sqlite3_value_int(values[valueIndex])
	}

	fun getAsDouble(valueIndex: Int) : Double {
		checkValueIndex(valueIndex)

		return sqlite3_value_double(values[valueIndex])
	}

	fun getAsBlob(valueIndex: Int) : ByteArray? {
		checkValueIndex(valueIndex)

		val blob = sqlite3_value_blob(values[valueIndex])
		val length = sqlite3_value_bytes(values[valueIndex])

		if (blob != null) {
			return blob.readBytes(length)
		} else if (sqlite3_value_type(values[valueIndex]) == SQLITE_NULL) {
			return byteArrayOf()
		}
		return null
	}
	
	// fun getAsPointer(valueIndex: Int) : Any {
	// 	checkValueIndex(valueIndex)

	// 	return sqlite3_value_pointer(values[valueIndex])
	// }
}