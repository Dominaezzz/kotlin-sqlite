package ksqlite

import kotlinx.cinterop.*
import sqlite3.*

inline class SQLiteContext(val ptr: CPointer<sqlite3_context>) {
	val db: SQLiteDatabase get() = SQLiteDatabase(sqlite3_context_db_handle(ptr)!!)

	fun setResult(value: String) {
		sqlite3_result_text(ptr, value, value.length, (-1).toLong().toCPointer())
	}

	fun setResult(value: ByteArray) {
	    sqlite3_result_blob(ptr, value.refTo(0), value.size, (-1).toLong().toCPointer())
	}

	fun setResult(value: Double) {
		sqlite3_result_double(ptr, value)
	}

	fun setResult(value: Int) {
		sqlite3_result_int(ptr, value)
	}

	fun setResult(value: Long) {
		sqlite3_result_int64(ptr, value)
	}

	fun setResultToNull() {
		sqlite3_result_null(ptr)
	}

	fun setResultToZeroBlob(count: Int) {
		sqlite3_result_zeroblob(ptr, count)
	}

	fun setResultToError(errorMessage: String) {
		sqlite3_result_error(ptr, errorMessage, errorMessage.length)
	}

//	fun setResultToSubType(subType: UInt) {
//		sqlite3_result_subtype(ptr, subType)
//	}
//
//	fun setResultToPointer(key: String, obj: Any) {
//		sqlite3_result_pointer(
//				ptr, StableRef.create(obj).asCPointer(), key,
//				staticCFunction { it -> it!!.asStableRef<Any>().dispose()  }
//		)
//	}

	fun setResultToValue(value: SQLiteValue) {
		sqlite3_result_value(ptr, value.ptr)
	}
}
