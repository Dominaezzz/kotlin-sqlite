package ksqlite

import kotlinx.cinterop.*
import sqlite3.*

interface SQLiteVirtualTableCursor {
	val rowId: Long
	val eof: Boolean
	fun filter(idxNum: Int, idxStr: String?, args: SQLiteValues)
	fun next()
	fun column(context: SQLiteContext, columnIndex: Int)
	fun close()
}
