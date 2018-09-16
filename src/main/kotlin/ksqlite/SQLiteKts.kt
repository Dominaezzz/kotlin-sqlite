package ksqlite


inline fun withSqlite(path: String, function: (SQLiteDatabase) -> Unit) {
	val db = SQLiteDatabase.open(path)
	try {
		function(db)
	} finally {
		db.close()
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