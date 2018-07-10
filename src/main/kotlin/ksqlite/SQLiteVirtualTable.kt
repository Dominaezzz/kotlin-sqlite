package ksqlite

import kotlinx.cinterop.*
import sqlite3.*

interface SQLiteVirtualTable {
	val declaration: String

	fun bestIndex(constraints: Array<SQLiteIndexConstraint>, orderBys: Array<SQLiteIndexOrderBy>, constraintUsages: Array<SQLiteIndexConstraintUsage>) : SQLiteIndexInfo
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

class SQLiteIndexConstraint(private val constraint: CPointer<sqlite3_index_constraint>) {
	val columnIndex: Int
		get() = constraint.pointed.iColumn
	
	val op: Byte
		get() = constraint.pointed.op
	
	val usable: Boolean
		get() = constraint.pointed.usable.toBoolean()
}

class SQLiteIndexOrderBy(private val orderBy: CPointer<sqlite3_index_orderby>) {
	val columnIndex: Int
		get() = orderBy.pointed.iColumn

	val desc: Boolean
		get() = orderBy.pointed.desc.toBoolean()
}

class SQLiteIndexConstraintUsage(private var constraintUsage: CPointer<sqlite3_index_constraint_usage>) {
	var argvIndex: Int
		get() = constraintUsage.pointed.argvIndex
		set(value) { constraintUsage.pointed.argvIndex = value }
	
	var omit: Boolean
		get() = constraintUsage.pointed.omit.toBoolean()
		set(value) { constraintUsage.pointed.omit = value.toByte() }
}

data class SQLiteIndexInfo(
	val idxNum: Int,
	val idxStr: String?,
	val orderByConsumed: Boolean,
	val estimatedCost: Double,
	
	val estimatedRows: Long,

	val idxFlags: Int,

	val columnsUsed: Long
)