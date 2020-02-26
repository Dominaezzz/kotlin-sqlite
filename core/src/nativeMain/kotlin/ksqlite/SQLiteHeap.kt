package ksqlite

import kotlinx.cinterop.*
import sqlite3.sqlite3_free
import sqlite3.sqlite3_malloc64

object SQLiteHeap : NativeFreeablePlacement {
	override fun alloc(size: Long, align: Int): NativePointed {
		val ptr = sqlite3_malloc64(size.toULong()) ?: throw OutOfMemoryError("sqlite3 can't allocate memory.")
		return interpretOpaquePointed(ptr.rawValue)
	}

	override fun free(mem: NativePtr) {
		sqlite3_free(interpretCPointer<COpaque>(mem))
	}
}
