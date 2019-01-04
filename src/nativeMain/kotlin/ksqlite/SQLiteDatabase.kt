package ksqlite

import kotlinx.cinterop.*
import sqlite3.*

inline class SQLiteDatabase(val ptr: CPointer<sqlite3>) {
	val fileName: String? get() = sqlite3_db_filename(ptr, "main")?.toKString()
	val version: String get() = sqlite3_version.toKString()
	val errorMessage: String? get() = sqlite3_errmsg(ptr)?.toKString()
	val lastInsertRowId: Long get() = sqlite3_last_insert_rowid(ptr)
	val changes: Int get() = sqlite3_changes(ptr)
	val totalChanges: Int get() = sqlite3_total_changes(ptr)

	fun execute(command: String, callback: ((Array<String>, Array<String>)-> Int)? = null) {
		memScoped {
			val error = alloc<CPointerVar<ByteVar>>()
			val result = if (callback == null) {
				sqlite3_exec(ptr, command, null, null, error.ptr)
			} else {
				val callbackStable = StableRef.create(callback)
				defer { callbackStable.dispose() }

				sqlite3_exec(ptr, command, staticCFunction { ptr, count, data, columns ->
					val callbackFunction = ptr!!.asStableRef<(Array<String>, Array<String>) -> Int>().get()
					val columnsArray = Array(count) { columns!![it]!!.toKString() }
					val dataArray = Array(count) { data!![it]!!.toKString() }
					callbackFunction(columnsArray, dataArray)
				}, callbackStable.asCPointer(), error.ptr)
			}
			defer { sqlite3_free(error.value) }

			if (result != 0) throw SQLiteError("DB error: ${error.value!!.toKString()}")
		}
	}

	fun prepare(sql: String) : Pair<SQLiteStatement, String?> = memScoped {
		val tailPtr = alloc<CPointerVar<ByteVar>>()
		val stmtPtr = alloc<CPointerVar<sqlite3_stmt>>()
		val result = sqlite3_prepare_v3(ptr, sql.cstr.ptr, sql.length, 0, stmtPtr.ptr, tailPtr.ptr)
		if (result != SQLITE_OK) {
			throw SQLiteError("Cannot prepare statement: ${sqlite3_errstr(result)?.toKString()}, $errorMessage")
		}
		SQLiteStatement(stmtPtr.value!!) to tailPtr.value?.toKString()
	}

	fun createFunction(name: String, nArg: Int, function: (SQLiteValues, SQLiteContext) -> Unit) {
		val result = sqlite3_create_function_v2(
			ptr, name, nArg, SQLITE_UTF8, StableRef.create(function).asCPointer(),
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

	fun createFunction(name: String, nArg: Int, function: SQLiteFunction) {
		val result = sqlite3_create_function_v2(
			ptr, name, nArg, SQLITE_UTF8, StableRef.create(function).asCPointer(),
			staticCFunction { ctx, nValues, values ->
				val func = sqlite3_user_data(ctx)!!.asStableRef<SQLiteFunction>().get()
				func(SQLiteValues(values!!, nValues), SQLiteContext(ctx!!))
			},
			null,
			null,
			staticCFunction { ptr -> ptr!!.asStableRef<SQLiteFunction>().dispose() }
		)
		check(result == SQLITE_OK) { "Could not create function!" }
	}

	fun createFunction(name: String, nArg: Int, function: SQLiteFunction.Aggregate) {
		val result = sqlite3_create_function_v2(
			ptr, name, nArg, SQLITE_UTF8, StableRef.create(function).asCPointer(),
			null,
			staticCFunction { ctx, nValues, values ->
				val func = sqlite3_user_data(ctx)!!.asStableRef<SQLiteFunction.Aggregate>().get()
				func.step(SQLiteValues(values!!, nValues), SQLiteContext(ctx!!))
			},
			staticCFunction { ctx ->
				val func = sqlite3_user_data(ctx)!!.asStableRef<SQLiteFunction.Aggregate>().get()
				func.final(SQLiteContext(ctx!!))
				sqlite3_aggregate_context(ctx, 0)?.run {
					reinterpret<COpaquePointerVar>().pointed.value!!.asStableRef<Any>().dispose()
				}
			},
			staticCFunction { ptr -> ptr!!.asStableRef<SQLiteFunction.Aggregate>().dispose() }
		)
		check(result == SQLITE_OK) { "Could not create function!" }
	}

	fun createModule(name: String, module: SQLiteModule) {
		inline fun CPointer<sqlite3_vtab>.getVirtualTable(): SQLiteVirtualTable {
			return reinterpret<ksqlite_vtab>().pointed.userObj!!.asStableRef<SQLiteVirtualTable>().get()
		}
		inline fun CPointer<sqlite3_vtab_cursor>.getVirtualTableCursor(): SQLiteVirtualTableCursor {
			return reinterpret<ksqlite_vtab_cursor>().pointed.userObj!!.asStableRef<SQLiteVirtualTableCursor>().get()
		}
		inline fun <reified T : SQLiteVirtualTable> CPointer<sqlite3_vtab>.getVirtualTableAndTry(errorCode: Int = SQLITE_ERROR, block: (T) -> Unit): Int {
			val virtualTable = getVirtualTable() as? T ?: return errorCode
			return try {
				block(virtualTable)
				SQLITE_OK
			} catch (t: Throwable) {
				pointed.zErrMsg = t.message?.let { sqlite3_mprintf(it) }
				SQLITE_ERROR
			}
		}
		inline fun CPointer<sqlite3_vtab_cursor>.withCursor(block: (SQLiteVirtualTableCursor) -> Unit): Int {
			val cursor = reinterpret<ksqlite_vtab_cursor>().pointed.userObj!!.asStableRef<SQLiteVirtualTableCursor>().get()
			return try {
				block(cursor)
				SQLITE_OK
			} catch (t: Throwable) {
				SQLITE_ERROR
			}
		}

		val rawModule = nativeHeap.alloc<sqlite3_module>()
		rawModule.xConnect = staticCFunction { dbPtr, pAux, argc, argv, ppVTab, pzErr ->
			val (moduleObj, _) = pAux!!.asStableRef<Pair<SQLiteModule, sqlite3_module>>().get()
			val vTabObj: SQLiteVirtualTable
			try {
				vTabObj = moduleObj.connect(SQLiteDatabase(dbPtr!!), Array(argc) { argv!![it]!!.toKString() })
				sqlite3_declare_vtab(dbPtr, vTabObj.declaration)
			} catch (t: Throwable) {
				pzErr!!.pointed.value = t.message?.let { sqlite3_mprintf(it) }
				return@staticCFunction SQLITE_ERROR
			}

			ppVTab!!.pointed.value = nativeHeap.alloc<ksqlite_vtab>().apply {
				userObj = StableRef.create(vTabObj).asCPointer()
			}.reinterpret<sqlite3_vtab>().ptr
			SQLITE_OK
		}
		rawModule.xDisconnect = staticCFunction { pVTab ->
			val ref = pVTab!!.reinterpret<ksqlite_vtab>().pointed.userObj!!.asStableRef<SQLiteVirtualTable>()
			nativeHeap.free(pVTab)
			val vTab = ref.get()
			ref.dispose()

			try {
				vTab.disconnect()
				SQLITE_OK
			} catch (t: Throwable) {
				SQLITE_ERROR
			}
		}
		if (module is SQLiteModule.Persist) { // If not eponymous.
			rawModule.xCreate = staticCFunction { dbPtr, pAux, argc, argv, ppVTab, pzErr ->
				val (moduleObj, _) = pAux!!.asStableRef<Pair<SQLiteModule.Persist, sqlite3_module>>().get()
				val vTabObj: SQLiteVirtualTable
				try {
					vTabObj = moduleObj.create(SQLiteDatabase(dbPtr!!), Array(argc) { argv!![it]!!.toKString() })
					sqlite3_declare_vtab(dbPtr, vTabObj.declaration)
				} catch (t: Throwable) {
					pzErr!!.pointed.value = t.message?.let { sqlite3_mprintf(it) }
					return@staticCFunction SQLITE_ERROR
				}

				ppVTab!!.pointed.value = nativeHeap.alloc<ksqlite_vtab>().run {
					userObj = StableRef.create(vTabObj).asCPointer()
					reinterpret<sqlite3_vtab>().ptr
				}
				SQLITE_OK
			}

			// This is required though....
			rawModule.xDestroy = staticCFunction { pVTab ->
				val ref = pVTab!!.reinterpret<ksqlite_vtab>().pointed.userObj!!.asStableRef<SQLiteVirtualTable.Persist>()
				nativeHeap.free(pVTab)
				val vTabObj = ref.get()
				ref.dispose()

				try {
					vTabObj.destroy()
					SQLITE_OK
				} catch (t: Throwable) {
					SQLITE_ERROR
				}
			}

			rawModule.xUpdate = staticCFunction { pVTab, argc, argv, pRowid ->
				pVTab!!.getVirtualTableAndTry<SQLiteVirtualTable.Persist>(SQLITE_READONLY) {
					pRowid!!.pointed.value = it.update(SQLiteValues(argv!!, argc))
				}
			}

			rawModule.xRename = staticCFunction { pVTab, pName ->
				val virtualTable = pVTab!!.getVirtualTable()
				if (virtualTable is SQLiteVirtualTable.Persist) {
					try {
						if (virtualTable.rename(pName!!.toKString())) {
							SQLITE_OK
						} else {
							SQLITE_ERROR
						}
					} catch (t: Throwable) {
						pVTab.pointed.zErrMsg = t.message?.let { sqlite3_mprintf(it) }
						SQLITE_ERROR
					}
				} else {
					SQLITE_READONLY
				}
			}
		}

		rawModule.xBestIndex = staticCFunction { pVTab, pIndexInfo ->
			val virtualTable = pVTab!!.getVirtualTable()

			val indexInfo = pIndexInfo!!.pointed

			try {
				virtualTable.bestIndex(
						Array(indexInfo.nConstraint) { Constraint(indexInfo.aConstraint!![it].ptr) },
						Array(indexInfo.nOrderBy) { OrderBy(indexInfo.aOrderBy!![it].ptr) },
						Array(indexInfo.nConstraint) { ConstraintUsage(indexInfo.aConstraintUsage!![it].ptr) },
						SQLiteIndexInfo(pIndexInfo)
				)
				SQLITE_OK
			} catch (t: Throwable) {
				SQLITE_ERROR
			}
		}

		rawModule.xOpen = staticCFunction { pVTab, ppCursor ->
			val virtualTable = pVTab!!.getVirtualTable()
			val virtualTableCursor = try {
				virtualTable.open()
			} catch (t: Throwable) {
				pVTab.pointed.zErrMsg = t.message?.let { sqlite3_mprintf(it) }
				return@staticCFunction SQLITE_ERROR
			}

			ppCursor!!.pointed.value = nativeHeap.alloc<ksqlite_vtab_cursor>().apply {
				userObj = StableRef.create(virtualTableCursor).asCPointer()
			}.reinterpret<sqlite3_vtab_cursor>().ptr

			SQLITE_OK
		}
		rawModule.xClose = staticCFunction { pCursor ->
			val ref = pCursor!!.reinterpret<ksqlite_vtab_cursor>().pointed.userObj!!.asStableRef<SQLiteVirtualTableCursor>()
			nativeHeap.free(pCursor)
			val virtualTableCursor = ref.get()
			ref.dispose()

			try {
				virtualTableCursor.close()
				SQLITE_OK
			} catch (t: Throwable) {
				SQLITE_ERROR
			}
		}
		rawModule.xEof = staticCFunction { pCursor ->
			if (pCursor!!.getVirtualTableCursor().eof) 1 else 0
		}
		rawModule.xFilter = staticCFunction { pCursor, idxNum, idxStr, argc, argv ->
			pCursor!!.withCursor { it.filter(idxNum, idxStr?.toKString(), SQLiteValues(argv!!, argc)) }
		}
		rawModule.xNext = staticCFunction { pCursor ->
			pCursor!!.withCursor { it.next() }
		}
		rawModule.xColumn = staticCFunction { pCursor, context, n ->
			pCursor!!.withCursor { it.column(SQLiteContext(context!!), n) }
		}
		rawModule.xRowid = staticCFunction { pCursor, pRowid ->
			pCursor!!.withCursor { pRowid!!.pointed.value = it.rowId }
		}

		if (false) {
			rawModule.xFindFunction = staticCFunction { pVTab, nArg, zName, pXFunc, ppArg ->
				val virtualTable = pVTab!!.getVirtualTable()
				try {

				} catch (t: Throwable) {
					return@staticCFunction SQLITE_ERROR
				}

				// TODO
				SQLITE_OK
			}
		}

		if (false) {
			rawModule.xBegin = staticCFunction { pVTab ->
				pVTab!!.getVirtualTableAndTry<SQLiteVirtualTable.Transactions> { it.begin() }
			}
			rawModule.xSync = staticCFunction { pVTab ->
				pVTab!!.getVirtualTableAndTry<SQLiteVirtualTable.Transactions> { it.sync() }
			}
			rawModule.xCommit = staticCFunction { pVTab ->
				pVTab!!.getVirtualTableAndTry<SQLiteVirtualTable.Transactions> { it.commit() }
			}
			rawModule.xRollback = staticCFunction { pVTab ->
				pVTab!!.getVirtualTableAndTry<SQLiteVirtualTable.Transactions> { it.rollback() }
			}
		}

		if (false) {
			rawModule.xSavepoint = staticCFunction { pVTab, n ->
				pVTab!!.getVirtualTableAndTry<SQLiteVirtualTable.Transactions.Nested> { it.savePoint(n) }
			}
			rawModule.xRelease = staticCFunction { pVTab, n ->
				pVTab!!.getVirtualTableAndTry<SQLiteVirtualTable.Transactions.Nested> { it.release(n) }
			}
			rawModule.xRollbackTo = staticCFunction { pVTab, n ->
				pVTab!!.getVirtualTableAndTry<SQLiteVirtualTable.Transactions.Nested> { it.rollbackTo(n) }
			}
		}

		val result = sqlite3_create_module_v2(ptr, name, rawModule.ptr,
				StableRef.create(module to rawModule).asCPointer(),
				staticCFunction { ptr ->
					with(ptr!!.asStableRef<Pair<SQLiteModule, sqlite3_module>>()) {
						nativeHeap.free(get().second)
						dispose()
					}
				}
		)
		check(result == SQLITE_OK) { "Failed to create module." }
	}

	fun setUpdateHook(hook: (Int, String, String, Long) -> Unit) {
		sqlite3_update_hook(ptr, staticCFunction { usrPtr, updateType, dbName, tableName, rowId ->
			val callback = usrPtr!!.asStableRef<(Int, String, String, Long) -> Unit>().get()
			callback(updateType, dbName!!.toKString(), tableName!!.toKString(), rowId)
		}, StableRef.create(hook).asCPointer())
	}

	override fun toString(): String = "SQLiteDatabase database in $fileName"

	fun close() {
		sqlite3_close_v2(ptr)
	}

	companion object {
		fun open(path: String = ":memory:") : SQLiteDatabase {
			return memScoped {
				val dbPtr = alloc<CPointerVar<sqlite3>>()
				if (sqlite3_open_v2(path, dbPtr.ptr, SQLITE_OPEN_READWRITE or SQLITE_OPEN_CREATE, null) != 0) {
					throw SQLiteError("Cannot open dbPtr: ${sqlite3_errmsg(dbPtr.value)?.toKString()}")
				}
				SQLiteDatabase(dbPtr.value!!)
			}
		}
	}
}
