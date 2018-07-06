package ksqlite

import kotlinx.cinterop.*
import sqlite3.*

abstract class SQLiteFunction(val name: String, val argumentCount: Int)

abstract class SQLiteScalarFunction(name: String, argumentCount: Int) : SQLiteFunction(name, argumentCount) {
	protected fun <T : Any> SQLiteContext.setMetadata(paramIndex: Int, metadata: T) {
		sqlite3_set_auxdata(context, paramIndex, StableRef.create(metadata).asCPointer(), staticCFunction { it ->
			it!!.asStableRef<Any>().dispose()
		})
	}

	protected fun <T : Any> SQLiteContext.getMetadata(paramIndex: Int) : T? {
		return sqlite3_get_auxdata(context, paramIndex)?.run { asStableRef<Any>().get() as T }
	}

	protected inline fun <T : Any> SQLiteContext.getMetadata(paramIndex: Int, create: () -> T) : T {
		return getMetadata<T>(paramIndex) ?: create().also { setMetadata(paramIndex, it) }
	}

	abstract fun function(values: SQLiteValues, context: SQLiteContext)
}

abstract class SQLiteAggregateFunction(name: String, argumentCount: Int) : SQLiteFunction(name, argumentCount) {
	protected fun <T : Any> SQLiteContext.setAggregateContext(aggCtx: T) {
		val aggCtxPtr = sqlite3_aggregate_context(context, COpaquePointerVar.size.toInt())!!.reinterpret<COpaquePointerVar>().pointed
		aggCtxPtr.value?.run { asStableRef<Any>().dispose() }
		aggCtxPtr.value = StableRef.create(aggCtx).asCPointer()
	}

	protected fun <T : Any> SQLiteContext.getAggregateContext() : T? {
		val rawAggCtx = sqlite3_aggregate_context(context, 0)
		if (rawAggCtx != null) {
			val aggCtxPtr = rawAggCtx.reinterpret<COpaquePointerVar>().pointed
			return aggCtxPtr.value!!.asStableRef<Any>().get() as T
		} else {
			return null
		}
	}

	protected inline fun <T : Any> SQLiteContext.getAggregateContext(create: () -> T) : T {
		return getAggregateContext<T>() ?: create().also { setAggregateContext(it) }
	}

	abstract fun step(values: SQLiteValues, context: SQLiteContext)
	abstract fun final(context: SQLiteContext)
}