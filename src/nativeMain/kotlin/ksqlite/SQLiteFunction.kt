package ksqlite

import kotlinx.cinterop.*
import sqlite3.*

abstract class SQLiteFunction {
	protected fun <T : Any> SQLiteContext.setMetadata(paramIndex: Int, metadata: T) {
		sqlite3_set_auxdata(ptr, paramIndex, StableRef.create(metadata).asCPointer(), staticCFunction { it ->
			it!!.asStableRef<Any>().dispose()
		})
	}

	protected fun <T : Any> SQLiteContext.getMetadata(paramIndex: Int) : T? {
		return sqlite3_get_auxdata(ptr, paramIndex)?.run { asStableRef<Any>().get() as T }
	}

	protected inline fun <T : Any> SQLiteContext.getMetadata(paramIndex: Int, create: () -> T) : T {
		return getMetadata(paramIndex) ?: create().also { setMetadata(paramIndex, it) }
	}

	abstract operator fun invoke(values: SQLiteValues, context: SQLiteContext)

	abstract class Aggregate {
		protected fun <T : Any> SQLiteContext.setAggregateContext(aggCtx: T) {
			val aggCtxPtr = sqlite3_aggregate_context(ptr, sizeOf<COpaquePointerVar>().toInt())!!.reinterpret<COpaquePointerVar>().pointed
			aggCtxPtr.value?.run { asStableRef<Any>().dispose() }
			aggCtxPtr.value = StableRef.create(aggCtx).asCPointer()
		}

		protected fun <T : Any> SQLiteContext.getAggregateContext() : T? {
			return sqlite3_aggregate_context(ptr, 0)?.let { rawAggCtx ->
				val aggCtxPtr = rawAggCtx.reinterpret<COpaquePointerVar>().pointed
				aggCtxPtr.value!!.asStableRef<Any>().get() as T
			}
		}

		protected inline fun <T : Any> SQLiteContext.getAggregateContext(create: () -> T) : T {
			return getAggregateContext() ?: create().also { setAggregateContext(it) }
		}

		abstract fun step(values: SQLiteValues, context: SQLiteContext)
		abstract fun final(context: SQLiteContext)
	}
}
