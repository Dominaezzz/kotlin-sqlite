package ksqlite

import kotlinx.cinterop.*
import sqlite3.*

class SQLiteValues internal constructor(private var values: CPointer<CPointerVar<sqlite3_value>>, override var size: Int) : AbstractList<SQLiteValue>() {
	override operator fun get(index: Int) : SQLiteValue {
		check(index in 0 until size) { "Value index out of bounds." }

		return SQLiteValue(values[index]!!)
	}
}