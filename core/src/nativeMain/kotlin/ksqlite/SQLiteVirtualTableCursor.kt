package ksqlite

import sqlite3.sqlite3_vtab_nochange

interface SQLiteVirtualTableCursor {
	val rowId: Long
	val eof: Boolean
	fun filter(idxNum: Int, idxStr: String?, args: SQLiteValues)
	fun next()
	fun column(context: SQLiteContext, columnIndex: Int)
	fun close() {}

	/**
	 * If [noChange] is called within [column], then it returns true if and only if the column
	 * is being fetched as part of an UPDATE operation during which the column value will not change.
	 * Applications might use this to substitute a return value that is less expensive to compute and
	 * that the corresponding [SQLiteVirtualTable.Persist.update] understands as a "no-change" value.
	 * If the [column] method calls [noChange] and finds that the column is not changed by the UPDATE statement,
	 * then the [column] method can optionally return without setting a result, without calling [SQLiteContext.setResult].
	 * In that case, [SQLiteVirtualTable.Persist.noChange] will return true for the same column in the [SQLiteVirtualTable.Persist.update] method.
	 * */
	val SQLiteContext.noChange: Boolean get() = sqlite3_vtab_nochange(ptr) != 0
}
