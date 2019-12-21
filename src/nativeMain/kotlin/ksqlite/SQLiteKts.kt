package ksqlite

inline fun usingSqlite(path: String, function: (SQLiteDatabase) -> Unit) {
	val db = SQLiteDatabase.open(path)
	try {
		function(db)
	} finally {
		db.close()
	}
}

inline fun <T> SQLiteDatabase.usingStmt(sql: String, function: (SQLiteStatement) -> T) : T {
	val (stmt, _) = prepare(sql)
	try {
		return function(stmt)
	} finally {
		stmt.close()
	}
}

inline fun withSqlite(path: String, function: SQLiteDatabase.() -> Unit) {
	val db = SQLiteDatabase.open(path)
	try {
		db.function()
	} finally {
		db.close()
	}
}

inline fun <T> SQLiteDatabase.withStmt(sql: String, function: SQLiteStatement.() -> T) : T {
	val (stmt, _) = prepare(sql)
	try {
		return stmt.function()
	} finally {
		stmt.close()
	}
}
