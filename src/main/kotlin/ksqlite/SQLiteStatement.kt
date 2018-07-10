package ksqlite

import kotlinx.cinterop.*
import sqlite3.*

class SQLiteStatement(private val db: SQLiteDatabase, sql: String) {
	var stmt: CPointer<sqlite3_stmt>? = null
	val sql: String
		get() = sqlite3_sql(stmt)!!.toKString()
	val expandedSql: String?
		get() = sqlite3_expanded_sql(stmt)?.let {
			try {
				it.toKString()
			} finally {
				sqlite3_free(it)
			}
		}
	val isBusy: Boolean
		get() = sqlite3_stmt_busy(stmt) == 1
	val isReadOnly: Boolean
		get() = sqlite3_stmt_readonly(stmt) == 1
	val parameterCount: Int
		get() = sqlite3_bind_parameter_count(stmt)
	val columnCount: Int
		get() = sqlite3_column_count(stmt)
	val dataCount: Int
		get() = sqlite3_data_count(stmt)


	init {
		stmt = memScoped {
			val stmtPtr = alloc<CPointerVar<sqlite3_stmt>>()
			// Do something about the left over sql statement(s).
			val result = sqlite3_prepare_v3(db.db, sql, sql.length, 0, stmtPtr.ptr, null)
			if (result != SQLITE_OK) {
				throw SQLiteError("Cannot prepare stmt: ${sqlite3_errstr(result)?.toKString()}")
			}
			stmtPtr.value
		}
	}

	fun step() : Boolean {
		return when (sqlite3_step(stmt)) {
			SQLITE_ROW -> true
			SQLITE_DONE -> false
			else -> throw SQLiteError("Could not step, error: ${db.errorMessage}")
		}
	}

	fun reset() {
		if (sqlite3_reset(stmt) != SQLITE_OK) {
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
		checkBind(sqlite3_bind_text(stmt, paramIndex, value, value.length, null))
	}

	fun bind(paramIndex: Int, value: Array<Byte>) {
        val blob = sqlite3_malloc(value.size)!!.reinterpret<ByteVar>()
        for (i in value.indices) { blob[i] = value[i] }
	    checkBind(sqlite3_bind_blob(stmt, paramIndex, blob, value.size, staticCFunction { b -> sqlite3_free(b) }))
	}

	fun bind(paramIndex: Int, value: Double) {
		checkBind(sqlite3_bind_double(stmt, paramIndex, value))
	}

	fun bind(paramIndex: Int, value: Int) {
		checkBind(sqlite3_bind_int(stmt, paramIndex, value))
	}

	fun bind(paramIndex: Int, value: Long) {
		checkBind(sqlite3_bind_int64(stmt, paramIndex, value))
	}

	fun bindNull(paramIndex: Int) {
		checkBind(sqlite3_bind_null(stmt, paramIndex))
	}

	fun bindZeroBlob(paramIndex: Int, count: Int) {
		checkBind(sqlite3_bind_zeroblob(stmt, paramIndex, count))
	}

	fun parameterName(paramIndex: Int) : String? = sqlite3_bind_parameter_name(stmt, paramIndex)?.toKString()

	fun parameterIndex(paramName: String) : Int = sqlite3_bind_parameter_index(stmt, paramName)

	fun clearBindings() {
        sqlite3_clear_bindings(stmt)
    }


	private fun checkColumnIndex(columnIndex: Int) {
		check(columnIndex in 0 until columnCount) { "Gave column index '$columnIndex', when column count is '$columnCount'." }
	}

	fun getColumnName(columnIndex: Int) : String {
		checkColumnIndex(columnIndex)

		return sqlite3_column_name(stmt, columnIndex)!!.toKString()
	}

	fun getColumnDeclaredType(columnIndex: Int) : String? {
		checkColumnIndex(columnIndex)
		
		return sqlite3_column_decltype(stmt, columnIndex)?.toKString()
	}

	fun getColumnString(columnIndex: Int) : String? {
		checkColumnIndex(columnIndex)

		return sqlite3_column_text(stmt, columnIndex)?.toKString()
	}

	fun getColumnLong(columnIndex: Int) : Long {
		checkColumnIndex(columnIndex)

		return sqlite3_column_int64(stmt, columnIndex)
	}

	fun getColumnInt(columnIndex: Int) : Int {
		checkColumnIndex(columnIndex)

		return sqlite3_column_int(stmt, columnIndex)
	}

	fun getColumnDouble(columnIndex: Int) : Double {
		checkColumnIndex(columnIndex)

		return sqlite3_column_double(stmt, columnIndex)
	}

	fun getColumnBlob(columnIndex: Int) : Array<Byte>? {
		checkColumnIndex(columnIndex)

		val blob = sqlite3_column_blob(stmt, columnIndex)
		val length = sqlite3_column_bytes(stmt, columnIndex)

		if (blob != null) {
			val bytes = blob.reinterpret<ByteVar>()
			return Array<Byte>(length, { index -> bytes[index] })
		} else if (sqlite3_column_type(stmt, columnIndex) == SQLITE_NULL) {
			return arrayOf<Byte>()
		}
		return null
	}

	fun close() {
		if (stmt != null) {
			sqlite3_finalize(stmt);
			stmt = null
		}
	}
}

inline fun SQLiteDatabase.withStmt(sql: String, function: (SQLiteStatement) -> Unit) {
	val stmt = SQLiteStatement(this, sql)
	try {
		function(stmt)
	} finally {
		stmt.close()
	}
}