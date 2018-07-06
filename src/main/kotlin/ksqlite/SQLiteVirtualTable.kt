package ksqlite

import kotlinx.cinterop.*
import sqlite3.*

interface SQLiteVirtualTable {
	val declaration: String

	// fun bestIndex(indexInfo: )
	fun open() : SQLiteVirtualTableCursor
	fun rename(newName: String) : Boolean
	fun destroy()
	fun disconnect()
	
	// (Optional)------------------

	// If not used then read-only.
	fun update(args: SQLiteValues) : Long
	
	fun findFunction(nArg: Int, name: String) : (SQLiteContext, SQLiteValues) -> Unit

	fun begin()
	fun commit()
	fun rollback()

	fun sync()

	fun savePoint(n: Int)
	fun release(n: Int)
	fun rollbackTo(n: Int)
}
