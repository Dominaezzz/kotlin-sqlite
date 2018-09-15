package ksqlite

import kotlinx.cinterop.*
import sqlite3.*

class SQLiteStatement constructor(val ptr: CPointer<sqlite3_stmt>) {
	val sql: String get() = sqlite3_sql(ptr)!!.toKString()
	val expandedSql: String? get() = sqlite3_expanded_sql(ptr)?.let {
			try {
				it.toKString()
			} finally {
				sqlite3_free(it)
			}
		}
	val isBusy: Boolean get() = sqlite3_stmt_busy(ptr) == 1
	val isReadOnly: Boolean get() = sqlite3_stmt_readonly(ptr) == 1
	val parameterCount: Int get() = sqlite3_bind_parameter_count(ptr)
	val columnCount: Int get() = sqlite3_column_count(ptr)
	val dataCount: Int get() = sqlite3_data_count(ptr)

	fun step() : Boolean {
		return when (sqlite3_step(ptr)) {
			SQLITE_ROW -> true
			SQLITE_DONE -> false
			else -> throw SQLiteError("Could not step, error: ${sqlite3_errmsg(sqlite3_db_handle(ptr))}")
		}
	}

	fun reset() {
		if (sqlite3_reset(ptr) != SQLITE_OK) {
			throw SQLiteError("Cannot reset.")
		}
	}

	private fun checkBind(result: Int) {
		when (result) {
			SQLITE_OK -> {}
			SQLITE_TOOBIG -> throw SQLiteError("Bind value too big!")
			SQLITE_RANGE -> throw SQLiteError("Index out of bounds.")
			SQLITE_NOMEM -> throw SQLiteError("Malloc failed")
			else -> throw SQLiteError("Bind failed")
		}
	}

	fun bind(paramIndex: Int, value: String) {
		checkBind(sqlite3_bind_text(ptr, paramIndex, value, value.length, (-1).toLong().toCPointer()))
	}

	fun bind(paramIndex: Int, value: ByteArray) {
        val blob = sqlite3_malloc(value.size)!!.reinterpret<ByteVar>()
        for (i in value.indices) { blob[i] = value[i] }
	    checkBind(sqlite3_bind_blob(ptr, paramIndex, blob, value.size, staticCFunction { ptr -> sqlite3_free(ptr) }))
	}

	fun bind(paramIndex: Int, value: Double) {
		checkBind(sqlite3_bind_double(ptr, paramIndex, value))
	}

	fun bind(paramIndex: Int, value: Int) {
		checkBind(sqlite3_bind_int(ptr, paramIndex, value))
	}

	fun bind(paramIndex: Int, value: Long) {
		checkBind(sqlite3_bind_int64(ptr, paramIndex, value))
	}

	fun bindNull(paramIndex: Int) {
		checkBind(sqlite3_bind_null(ptr, paramIndex))
	}

	fun bindZeroBlob(paramIndex: Int, count: Int) {
		checkBind(sqlite3_bind_zeroblob(ptr, paramIndex, count))
	}

	fun parameterName(paramIndex: Int) : String? = sqlite3_bind_parameter_name(ptr, paramIndex)?.toKString()

	fun parameterIndex(paramName: String) : Int = sqlite3_bind_parameter_index(ptr, paramName)

	fun clearBindings() {
        sqlite3_clear_bindings(ptr)
    }


	private fun checkColumnIndex(columnIndex: Int) {
		check(columnIndex in 0 until columnCount) { "Gave column index '$columnIndex', when column count is '$columnCount'." }
	}

	fun getColumnName(columnIndex: Int) : String {
		checkColumnIndex(columnIndex)

		return sqlite3_column_name(ptr, columnIndex)!!.toKString()
	}

	fun getColumnDeclaredType(columnIndex: Int) : String? {
		checkColumnIndex(columnIndex)
		
		return sqlite3_column_decltype(ptr, columnIndex)?.toKString()
	}

	fun getColumnString(columnIndex: Int) : String? {
		checkColumnIndex(columnIndex)

		return sqlite3_column_text(ptr, columnIndex)?.toKString()
	}

	fun getColumnLong(columnIndex: Int) : Long {
		checkColumnIndex(columnIndex)

		return sqlite3_column_int64(ptr, columnIndex)
	}

	fun getColumnInt(columnIndex: Int) : Int {
		checkColumnIndex(columnIndex)

		return sqlite3_column_int(ptr, columnIndex)
	}

	fun getColumnDouble(columnIndex: Int) : Double {
		checkColumnIndex(columnIndex)

		return sqlite3_column_double(ptr, columnIndex)
	}

	fun getColumnBlob(columnIndex: Int) : ByteArray? {
		checkColumnIndex(columnIndex)

		val blob = sqlite3_column_blob(ptr, columnIndex)
		val length = sqlite3_column_bytes(ptr, columnIndex)

		if (blob != null) {
			return blob.readBytes(length)
		} else if (sqlite3_column_type(ptr, columnIndex) == SQLITE_NULL) {
			return byteArrayOf()
		}
		return null
	}

	fun close() {
		sqlite3_finalize(ptr)
	}
}

inline fun <T> SQLiteDatabase.withStmt(sql: String, function: (SQLiteStatement) -> T) : T {
	val (stmt, _) = prepare(sql)
	try {
		return function(stmt)
	} finally {
		stmt.close()
	}
}