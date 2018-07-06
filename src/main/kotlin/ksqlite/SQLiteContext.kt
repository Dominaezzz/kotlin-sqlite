package ksqlite

import kotlinx.cinterop.*
import sqlite3.*

class SQLiteContext internal constructor(internal var context: CPointer<sqlite3_context>) {

	fun setResult(value: String) {
		sqlite3_result_text(context, value, value.length, null)
	}

	fun setResult(value: Array<Byte>) {
        val blob = sqlite3_malloc(value.size)!!.reinterpret<ByteVar>()
        for (i in value.indices) { blob[i] = value[i] }
	    sqlite3_result_blob(context, blob, value.size, staticCFunction { b -> sqlite3_free(b) })
	}

	fun setResult(value: Double) {
		sqlite3_result_double(context, value)
	}

	fun setResult(value: Int) {
		sqlite3_result_int(context, value)
	}

	fun setResult(value: Long) {
		sqlite3_result_int64(context, value)
	}

	fun setResultToNull() {
		sqlite3_result_null(context)
	}

	fun setResultToZeroBlob(count: Int) {
		sqlite3_result_zeroblob(context, count)
	}

	fun setResultToError(errorMessage: String) {
		sqlite3_result_error(context, errorMessage, errorMessage.length)
	}

	// TODO Implement subType, setResultToPointer and maybe value.
}
