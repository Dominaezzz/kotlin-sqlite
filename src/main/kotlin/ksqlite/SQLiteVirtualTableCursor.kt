package ksqlite

import kotlinx.cinterop.*
import sqlite3.*

interface SQLiteVirtualTableCursor {
	fun close()
	fun filter(idxNum: Int, idxStr: String, args: SQLiteValues)
	fun next()
	fun eof() : Boolean
	fun column(context: SQLiteContext, columnIndex: Int)
	fun rowId() : Long
}
