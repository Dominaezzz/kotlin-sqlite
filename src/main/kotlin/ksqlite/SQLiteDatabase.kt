package ksqlite

import kotlinx.cinterop.*
import sqlite3.*

class SQLiteError(message: String) : Error(message)

private fun fromCArray(ptr: CPointer<CPointerVar<ByteVar>>, count: Int) =
		Array(count, { index -> (ptr+index)!!.pointed.value!!.toKString() })

class SQLiteDatabase(dbPath: String = ":memory:") {
	var dbPath: String = ""
	var db: CPointer<sqlite3>? = null
	val version: String
		get() = sqlite3_version.toKString()
	val errorMessage: String?
		get() = sqlite3_errmsg(db)?.toKString()

	init {
		db = memScoped {
			val dbPtr = alloc<CPointerVar<sqlite3>>()
			if (sqlite3_open_v2(dbPath, dbPtr.ptr, SQLITE_OPEN_READWRITE or SQLITE_OPEN_CREATE, null) != 0) {
				throw SQLiteError("Cannot open db: ${errorMessage}")
			}
			dbPtr.value
		}
	}

	fun execute(command: String, callback: ((Array<String>, Array<String>)-> Int)? = null) {
		memScoped {
			val error = alloc<CPointerVar<ByteVar>>()
			val callbackStable = callback?.let { StableRef.create(it) }
			try {
				val result = sqlite3_exec(
					db, command,
					if (callback != null) {
						staticCFunction { ptr, count, data, columns ->
							val callbackFunction = ptr!!.asStableRef<(Array<String>, Array<String>) -> Int>().get()
							val columnsArray = fromCArray(columns!!, count)
							val dataArray = fromCArray(data!!, count)
							callbackFunction(columnsArray, dataArray)
						}
					} else null,
					callbackStable?.asCPointer(), error.ptr
				)
				
				if (result != 0) throw SQLiteError("DB error: ${error.value!!.toKString()}")
			} finally {
				callbackStable?.dispose()
				sqlite3_free(error.value)
			}
		}
	}

	fun createFunction(name: String, nArg: Int, function: (SQLiteValues, SQLiteContext) -> Unit) {
		val result = sqlite3_create_function_v2(
			db, name, nArg, SQLITE_UTF8, StableRef.create(function).asCPointer(),
			staticCFunction { ctx, nValues, values ->
				val func = sqlite3_user_data(ctx)!!.asStableRef<(SQLiteValues, SQLiteContext) -> Unit>().get()
				func(SQLiteValues(values!!, nValues), SQLiteContext(ctx!!))
			},
			null,
			null,
			staticCFunction { ptr -> ptr!!.asStableRef<(SQLiteValues, SQLiteContext) -> Unit>().dispose() }
		)
		check(result == SQLITE_OK) { "Could not create function!" }
	}

	fun createFunction(function: SQLiteScalarFunction) {
		val result = sqlite3_create_function_v2(
			db, function.name, function.argumentCount, SQLITE_UTF8, StableRef.create(function).asCPointer(),
			staticCFunction { ctx, nValues, values ->
				val func = sqlite3_user_data(ctx)!!.asStableRef<SQLiteScalarFunction>().get()
				func.function(SQLiteValues(values!!, nValues), SQLiteContext(ctx!!))
			},
			null,
			null,
			staticCFunction { ptr -> ptr!!.asStableRef<SQLiteScalarFunction>().dispose() }
		)
		check(result == SQLITE_OK) { "Could not create function!" }
	}

	fun createFunction(function: SQLiteAggregateFunction) {
		val result = sqlite3_create_function_v2(
			db, function.name, function.argumentCount, SQLITE_UTF8, StableRef.create(function).asCPointer(),
			null,
			staticCFunction { ctx, nValues, values ->
				val func = sqlite3_user_data(ctx)!!.asStableRef<SQLiteAggregateFunction>().get()
				func.step(SQLiteValues(values!!, nValues), SQLiteContext(ctx!!))
			},
			staticCFunction { ctx ->
				val func = sqlite3_user_data(ctx)!!.asStableRef<SQLiteAggregateFunction>().get()
				func.final(SQLiteContext(ctx!!))
				sqlite3_aggregate_context(ctx, 0)?.run {
					reinterpret<COpaquePointerVar>().pointed.value!!.asStableRef<Any>().dispose()
				}
			},
			staticCFunction { ptr -> ptr!!.asStableRef<SQLiteAggregateFunction>().dispose() }
		)
		check(result == SQLITE_OK) { "Could not create function!" }
	}

	fun createModule(name: String, module: SQLiteModule) {
		val result = memScoped {
			val rawModule = alloc<sqlite3_module>()
			if (false) { // If not eponymous.
				rawModule.xCreate = staticCFunction { dbPtr, pAux, argc, argv, ppVTab, pzErr ->
					val args = fromCArray(argv!!, argc)
					// Call module's create.
					SQLITE_OK
				}

				// This is required though....
				rawModule.xDestroy = staticCFunction { pVTab ->
					SQLITE_OK
				}
			}
			rawModule.xConnect = staticCFunction { dbPtr, pAux, argc, argv, ppVTab, pzErr ->
				val args = fromCArray(argv!!, argc)
				
				val moduleObj = pAux!!.asStableRef<SQLiteModule>().get()
				val vTabObj = moduleObj.connect(SQLiteDatabase(), args)

				ppVTab!!.pointed.value = nativeHeap.alloc<ksqlite_vtab>().apply {
					userObj = StableRef.create(vTabObj).asCPointer()
				}
				.reinterpret<sqlite3_vtab>().ptr

				sqlite3_declare_vtab(dbPtr, vTabObj.declaration)

				SQLITE_OK
			}
			rawModule.xDisconnect = staticCFunction { pVTab ->
				pVTab!!.reinterpret<ksqlite_vtab>().pointed.userObj!!.asStableRef<SQLiteVirtualTable>().apply {
					get().disconnect()
					dispose()
				}
				nativeHeap.free(pVTab)

				SQLITE_OK
			}

			rawModule.xOpen = staticCFunction { pVTab, ppCursor ->
				val virtualTable = pVTab!!.reinterpret<ksqlite_vtab>().pointed.userObj!!.asStableRef<SQLiteVirtualTable>().get()

				ppCursor!!.pointed.value = nativeHeap.alloc<ksqlite_vtab_cursor>().apply {
					userObj = StableRef.create(virtualTable.open()).asCPointer()
				}
				.reinterpret<sqlite3_vtab_cursor>().ptr

				SQLITE_OK
			}
			rawModule.xClose = staticCFunction { pCursor ->
				pCursor!!.reinterpret<ksqlite_vtab_cursor>().pointed.userObj!!.asStableRef<SQLiteVirtualTableCursor>().apply {
					get().close()
					dispose()
				}
				nativeHeap.free(pCursor)
				SQLITE_OK
			}

			rawModule.xEof = staticCFunction { pCursor ->
				if (pCursor!!.reinterpret<ksqlite_vtab_cursor>().pointed
					.userObj!!.asStableRef<SQLiteVirtualTableCursor>().get().eof()) 1 else 0
			}

			rawModule.xFilter = staticCFunction { pCursor, idxNum, idxStr, argc, argv ->
				pCursor!!.reinterpret<ksqlite_vtab_cursor>().pointed
					.userObj!!.asStableRef<SQLiteVirtualTableCursor>().get()
					.filter(idxNum, idxStr!!.toKString(), SQLiteValues(argv!!, argc))
				SQLITE_OK
			}

			rawModule.xNext = staticCFunction { pCursor ->
				pCursor!!.reinterpret<ksqlite_vtab_cursor>().pointed
					.userObj!!.asStableRef<SQLiteVirtualTableCursor>().get()
					.next()
				SQLITE_OK
			}

			rawModule.xColumn = staticCFunction { pCursor, context, n ->
				pCursor!!.reinterpret<ksqlite_vtab_cursor>().pointed
					.userObj!!.asStableRef<SQLiteVirtualTableCursor>().get()
					.column(SQLiteContext(context!!), n)
				SQLITE_OK
			}

			rawModule.xRowid = staticCFunction { pCursor, pRowid ->
				pRowid!!.pointed.value = pCursor!!.reinterpret<ksqlite_vtab_cursor>().pointed
					.userObj!!.asStableRef<SQLiteVirtualTableCursor>().get().rowId()
				SQLITE_OK
			}

			if (false) {
				rawModule.xUpdate = staticCFunction { pVTab, argc, argv, pRowid ->
					val virtualTable = pVTab!!.reinterpret<ksqlite_vtab>().pointed.userObj!!.asStableRef<SQLiteVirtualTable>().get()
					pRowid!!.pointed.value = virtualTable.update(SQLiteValues(argv!!, argc))
					SQLITE_OK
				}
			}

			if (false) {
				rawModule.xFindFunction = staticCFunction { pVTab, nArg, zName, pXFunc, ppArg ->
					// TODO
					SQLITE_OK
				}
			}

			if (false) {
				rawModule.xBegin = staticCFunction { pVTab ->
					SQLITE_OK
				}
				rawModule.xSync = staticCFunction { pVTab ->
					SQLITE_OK
				}
				rawModule.xCommit = staticCFunction { pVTab ->
					SQLITE_OK
				}
				rawModule.xRollback = staticCFunction { pVTab ->
					SQLITE_OK
				}
			}
			
			rawModule.xRename = staticCFunction { pVTab, pName ->
				val virtualTable = pVTab!!.reinterpret<ksqlite_vtab>().pointed.userObj!!.asStableRef<SQLiteVirtualTable>().get()
				virtualTable.rename(pName!!.toKString())
				SQLITE_OK
			}

			if (false) {
				rawModule.xSavepoint = staticCFunction { pVTab, n ->
					val virtualTable = pVTab!!.reinterpret<ksqlite_vtab>().pointed.userObj!!.asStableRef<SQLiteVirtualTable>().get()
					virtualTable.savePoint(n)
					SQLITE_OK
				}
				rawModule.xRelease = staticCFunction { pVTab, n ->
					val virtualTable = pVTab!!.reinterpret<ksqlite_vtab>().pointed.userObj!!.asStableRef<SQLiteVirtualTable>().get()
					virtualTable.release(n)
					SQLITE_OK
				}
				rawModule.xRollbackTo = staticCFunction { pVTab, n ->
					val virtualTable = pVTab!!.reinterpret<ksqlite_vtab>().pointed.userObj!!.asStableRef<SQLiteVirtualTable>().get()
					virtualTable.rollbackTo(n)
					SQLITE_OK
				}
			}

			sqlite3_create_module_v2(
				db, name, rawModule.ptr, StableRef.create(module).asCPointer(), staticCFunction { ptr ->
					ptr!!.asStableRef<SQLiteModule>().dispose()
				}
			)
		}
	}

	override fun toString(): String = "SQLiteDatabase database in $dbPath"

	fun close() {
		if (db != null) {
			sqlite3_close_v2(db)
			db = null
		}
	}
}

inline fun withSqlite(path: String, function: (SQLiteDatabase) -> Unit) {
	val db = SQLiteDatabase(path)
	try {
		function(db)
	} finally {
		db.close()
	}
}
