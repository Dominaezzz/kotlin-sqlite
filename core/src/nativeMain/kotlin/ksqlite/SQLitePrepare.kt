package ksqlite

import sqlite3.SQLITE_PREPARE_NORMALIZE
import sqlite3.SQLITE_PREPARE_NO_VTAB
import sqlite3.SQLITE_PREPARE_PERSISTENT

enum class SQLitePrepare(override val value: Int) : Flag<SQLitePrepare> {
	Persistent(SQLITE_PREPARE_PERSISTENT),
	Normalize(SQLITE_PREPARE_NORMALIZE),
	NoVTab(SQLITE_PREPARE_NO_VTAB);

	override val info: Flag.EnumInfo<SQLitePrepare> get() = enumInfo

	companion object {
		private val enumInfo = Flag.enumInfo<SQLitePrepare>()
	}
}
