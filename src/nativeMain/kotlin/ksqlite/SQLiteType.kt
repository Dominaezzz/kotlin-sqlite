package ksqlite

import sqlite3.*

enum class SQLiteType(val value: Int) {
	Null(SQLITE_NULL),
	Number(SQLITE_INTEGER),
	Float(SQLITE_FLOAT),
	Text(SQLITE_TEXT),
	Blob(SQLITE_BLOB)
}