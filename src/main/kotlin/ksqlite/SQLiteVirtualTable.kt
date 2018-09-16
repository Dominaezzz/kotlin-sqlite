package ksqlite

import kotlinx.cinterop.*
import sqlite3.*

interface SQLiteVirtualTable {
	val declaration: String

	fun bestIndex(constraints: Array<SQLiteIndex.Constraint>, orderBys: Array<SQLiteIndex.OrderBy>, constraintUsages: Array<SQLiteIndex.ConstraintUsage>, info: SQLiteIndex.Info)
	fun open() : SQLiteVirtualTableCursor
	fun rename(newName: String) : Boolean
	fun destroy()
	fun disconnect()
	
	// (Optional)------------------

	// If not used then read-only.
	fun update(args: SQLiteValues) : Long
	
	// fun findFunction(nArg: Int, name: String) : (SQLiteContext, SQLiteValues) -> Unit
}

interface SQLiteVirtualTableTransactions {
	fun begin()
	fun commit()
	fun rollback()

	fun sync()
}

interface SQLiteVirtualTableNestedTransactions {
	fun savePoint(n: Int)
	fun release(n: Int)
	fun rollbackTo(n: Int)
}

sealed class SQLiteIndex {
	class Info(private val ptr: CPointer<sqlite3_index_info>) {
		var idxNum: Int
			get() = ptr.pointed.idxNum
			set(value) { ptr.pointed.idxNum = value }

		var idxStr: String?
			get() = ptr.pointed.idxStr?.toKString()
			set(value) {
				if (ptr.pointed.needToFreeIdxStr == 1) {
					sqlite3_free(ptr.pointed.idxStr)
				}
				ptr.pointed.idxStr = value?.let { sqlite3_mprintf(it) }
				ptr.pointed.needToFreeIdxStr = if (value != null) 1 else 0
			}

		var orderByConsumed: Boolean
			get() = ptr.pointed.orderByConsumed == 1
			set(value) { ptr.pointed.orderByConsumed = if (value) 1 else 0 }

		var estimatedCost: Double
			get() = ptr.pointed.estimatedCost
			set(value) { ptr.pointed.estimatedCost = value }

		var estimatedRows: Long
			get() = ptr.pointed.estimatedRows
			set(value) { ptr.pointed.estimatedRows = value }

		var idxFlags: Int
			get() = ptr.pointed.idxFlags
			set(value) { ptr.pointed.idxFlags = value }

		var columnsUsed: Long
			get() = ptr.pointed.colUsed
			set(value) { ptr.pointed.colUsed = value}
	}

	class Constraint(private val constraint: CPointer<sqlite3_index_constraint>) {
		val columnIndex: Int
			get() = constraint.pointed.iColumn

		val op: Byte
			get() = constraint.pointed.op

		val usable: Boolean
			get() = constraint.pointed.usable.toBoolean()
	}

	class OrderBy(private val orderBy: CPointer<sqlite3_index_orderby>) {
		val columnIndex: Int
			get() = orderBy.pointed.iColumn

		val desc: Boolean
			get() = orderBy.pointed.desc.toBoolean()
	}

	class ConstraintUsage(private val constraintUsage: CPointer<sqlite3_index_constraint_usage>) {
		var argvIndex: Int
			get() = constraintUsage.pointed.argvIndex
			set(value) { constraintUsage.pointed.argvIndex = value }

		var omit: Boolean
			get() = constraintUsage.pointed.omit.toBoolean()
			set(value) { constraintUsage.pointed.omit = value.toByte() }
	}
}
