package ksqlite

import sqlite3.*

enum class SQLiteOpen(override val value: Int) : Flag<SQLiteOpen> {
	/* Ok for sqlite3_open_v2() */
	READONLY(SQLITE_OPEN_READONLY),

	/* Ok for sqlite3_open_v2() */
	READWRITE(SQLITE_OPEN_READWRITE),

	/* Ok for sqlite3_open_v2() */
	CREATE(SQLITE_OPEN_CREATE),

	/* VFS only */
	DELETEONCLOSE(SQLITE_OPEN_DELETEONCLOSE),

	/* VFS only */
	EXCLUSIVE(SQLITE_OPEN_EXCLUSIVE),

	/* VFS only */
	AUTOPROXY(SQLITE_OPEN_AUTOPROXY),

	/* Ok for sqlite3_open_v2() */
	URI(SQLITE_OPEN_URI),

	/* Ok for sqlite3_open_v2() */
	MEMORY(SQLITE_OPEN_MEMORY),

	/* VFS only */
	MAIN_DB(SQLITE_OPEN_MAIN_DB),

	/* VFS only */
	TEMP_DB(SQLITE_OPEN_TEMP_DB),

	/* VFS only */
	TRANSIENT_DB(SQLITE_OPEN_TRANSIENT_DB),

	/* VFS only */
	MAIN_JOURNAL(SQLITE_OPEN_MAIN_JOURNAL),

	/* VFS only */
	TEMP_JOURNAL(SQLITE_OPEN_TEMP_JOURNAL),

	/* VFS only */
	SUBJOURNAL(SQLITE_OPEN_SUBJOURNAL),

	/* VFS only */
	MASTER_JOURNAL(SQLITE_OPEN_MASTER_JOURNAL),

	/* Ok for sqlite3_open_v2() */
	NOMUTEX(SQLITE_OPEN_NOMUTEX),

	/* Ok for sqlite3_open_v2() */
	FULLMUTEX(SQLITE_OPEN_FULLMUTEX),

	/* Ok for sqlite3_open_v2() */
	SHAREDCACHE(SQLITE_OPEN_SHAREDCACHE),

	/* Ok for sqlite3_open_v2() */
	PRIVATECACHE(SQLITE_OPEN_PRIVATECACHE),

	/* VFS only */
	WAL(SQLITE_OPEN_WAL);

	override val info: Flag.EnumInfo<SQLiteOpen> get() = enumInfo

	companion object {
		private val enumInfo = Flag.enumInfo<SQLiteOpen>()
	}
}
