package ksqlite

import kotlinx.cinterop.*
import sqlite3.*

class SQLiteDatabase(val ptr: CPointer<sqlite3>) {
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
		val result = sqlite3_prepare_v3(ptr, sql, sql.length, 0, stmtPtr.ptr, tailPtr.ptr)
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

	fun createFunction(function: SQLiteScalarFunction) {
		val result = sqlite3_create_function_v2(
				ptr, function.name, function.argumentCount, SQLITE_UTF8, StableRef.create(function).asCPointer(),
			staticCFunction { ctx, nValues, values ->
				val func = sqlite3_user_data(ctx)!!.asStableRef<SQLiteScalarFunction>().get()
				func(SQLiteValues(values!!, nValues), SQLiteContext(ctx!!))
			},
			null,
			null,
			staticCFunction { ptr -> ptr!!.asStableRef<SQLiteScalarFunction>().dispose() }
		)
		check(result == SQLITE_OK) { "Could not create function!" }
	}

	fun createFunction(function: SQLiteAggregateFunction) {
		val result = sqlite3_create_function_v2(
				ptr, function.name, function.argumentCount, SQLITE_UTF8, StableRef.create(function).asCPointer(),
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
			if (module is SQLiteModule.Persist) { // If not eponymous.
				rawModule.xCreate = staticCFunction { dbPtr, pAux, argc, argv, ppVTab, pzErr ->
					val moduleObj = pAux!!.asStableRef<SQLiteModule.Persist>().get()
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
					val ref = pVTab!!.reinterpret<ksqlite_vtab>().pointed.userObj!!.asStableRef<SQLiteVirtualTable>()
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
			}
			rawModule.xConnect = staticCFunction { dbPtr, pAux, argc, argv, ppVTab, pzErr ->
				val moduleObj = pAux!!.asStableRef<SQLiteModule>().get()
				val vTabObj: SQLiteVirtualTable
				try {
					vTabObj = moduleObj.connect(SQLiteDatabase(dbPtr!!), Array(argc) { argv!![it]!!.toKString() })
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

			rawModule.xBestIndex = staticCFunction { pVTab, pIndexInfo ->
				val virtualTable = pVTab!!.reinterpret<ksqlite_vtab>().pointed.userObj!!.asStableRef<SQLiteVirtualTable>().get()

				val indexInfo = pIndexInfo!!.pointed

				try {
					virtualTable.bestIndex(
							Array(indexInfo.nConstraint) { SQLiteIndex.Constraint(indexInfo.aConstraint!![it].ptr) },
							Array(indexInfo.nOrderBy) { SQLiteIndex.OrderBy(indexInfo.aOrderBy!![it].ptr) },
							Array(indexInfo.nConstraint) { SQLiteIndex.ConstraintUsage(indexInfo.aConstraintUsage!![it].ptr) },
							SQLiteIndex.Info(pIndexInfo)
					)
					SQLITE_OK
				} catch (t: Throwable) {
					SQLITE_ERROR
				}
			}

			rawModule.xOpen = staticCFunction { pVTab, ppCursor ->
				val virtualTable = pVTab!!.reinterpret<ksqlite_vtab>().pointed.userObj!!.asStableRef<SQLiteVirtualTable>().get()
				val virtualTableCursor: SQLiteVirtualTableCursor
				try {
					virtualTableCursor = virtualTable.open()
				} catch (t: Throwable) {
					return@staticCFunction SQLITE_ERROR
				}

				ppCursor!!.pointed.value = nativeHeap.alloc<ksqlite_vtab_cursor>().apply {
					userObj = StableRef.create(virtualTableCursor).asCPointer()
				}
				.reinterpret<sqlite3_vtab_cursor>().ptr

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
				if (pCursor!!.reinterpret<ksqlite_vtab_cursor>().pointed
					.userObj!!.asStableRef<SQLiteVirtualTableCursor>().get().eof) 1 else 0
			}

			rawModule.xFilter = staticCFunction { pCursor, idxNum, idxStr, argc, argv ->
				val cursor = pCursor!!.reinterpret<ksqlite_vtab_cursor>().pointed
					.userObj!!.asStableRef<SQLiteVirtualTableCursor>().get()

				try {
					cursor.filter(idxNum, idxStr?.toKString(), SQLiteValues(argv!!, argc))
					SQLITE_OK
				} catch (t: Throwable) {
					SQLITE_ERROR
				}
			}

			rawModule.xNext = staticCFunction { pCursor ->
				try {
					pCursor!!.reinterpret<ksqlite_vtab_cursor>().pointed
							.userObj!!.asStableRef<SQLiteVirtualTableCursor>().get()
							.next()
					SQLITE_OK
				} catch (t: Throwable) {
					SQLITE_ERROR
				}
			}

			rawModule.xColumn = staticCFunction { pCursor, context, n ->
				try {
					pCursor!!.reinterpret<ksqlite_vtab_cursor>().pointed
							.userObj!!.asStableRef<SQLiteVirtualTableCursor>().get()
							.column(SQLiteContext(context!!), n)
					SQLITE_OK
				} catch (t: Throwable) {
					SQLITE_ERROR
				}
			}

			rawModule.xRowid = staticCFunction { pCursor, pRowid ->
				try {
					pRowid!!.pointed.value = pCursor!!.reinterpret<ksqlite_vtab_cursor>().pointed
							.userObj!!.asStableRef<SQLiteVirtualTableCursor>().get().rowId
					SQLITE_OK
				} catch (t: Throwable) {
					SQLITE_ERROR
				}
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
				try {
					virtualTable.rename(pName!!.toKString())
					SQLITE_OK
				} catch (t: Throwable) {
					SQLITE_ERROR
				}
			}

			if (false) {
				// rawModule.xSavepoint = staticCFunction { pVTab, n ->
				// 	val virtualTable = pVTab!!.reinterpret<ksqlite_vtab>().pointed.userObj!!.asStableRef<SQLiteVirtualTable>().get()
				// 	virtualTable.savePoint(n)
				// 	SQLITE_OK
				// }
				// rawModule.xRelease = staticCFunction { pVTab, n ->
				// 	val virtualTable = pVTab!!.reinterpret<ksqlite_vtab>().pointed.userObj!!.asStableRef<SQLiteVirtualTable>().get()
				// 	virtualTable.release(n)
				// 	SQLITE_OK
				// }
				// rawModule.xRollbackTo = staticCFunction { pVTab, n ->
				// 	val virtualTable = pVTab!!.reinterpret<ksqlite_vtab>().pointed.userObj!!.asStableRef<SQLiteVirtualTable>().get()
				// 	virtualTable.rollbackTo(n)
				// 	SQLITE_OK
				// }
			}

			sqlite3_create_module_v2(
					ptr, name, rawModule.ptr, StableRef.create(module).asCPointer(),
					staticCFunction { ptr -> ptr!!.asStableRef<SQLiteModule>().dispose() }
			)
		}
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
