package ksqlite

import kotlinx.cinterop.*
import sqlite3.*

abstract class SQLiteModule {
	/** Called to connect to an existing table. */
	abstract fun connect(db: SQLiteDatabase, args: Array<String>) : SQLiteVirtualTable

	interface Persist {
		/** Called on CREATE VIRTUAL TABLE.... (Optional) */
		fun create(db: SQLiteDatabase, args: Array<String>) : SQLiteVirtualTable
	}
}