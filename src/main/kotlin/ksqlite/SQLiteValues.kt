package ksqlite

import kotlinx.cinterop.*
import sqlite3.*

class SQLiteValues internal constructor(private var values: CPointer<CPointerVar<sqlite3_value>>, var count: Int) {
	operator fun get(valueIndex: Int) : SQLiteValue {
		check(valueIndex in 0 until count) { "Value index out of bounds." }

		return SQLiteValue(values[valueIndex]!!)
	}
}