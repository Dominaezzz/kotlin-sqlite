package ksqlite

import kotlinx.cinterop.*
import sqlite3.*

class SQLiteError(message: String) : Error(message)

class SQLiteDatabase(val dbPtr: CPointer<sqlite3>) {
	val fileName: String? get() = sqlite3_db_filename(dbPtr, "main")?.toKString()
	val version: String get() = sqlite3_version.toKString()
	val errorMessage: String? get() = sqlite3_errmsg(dbPtr)?.toKString()
	val lastInsertRowId: Long get() = sqlite3_last_insert_rowid(dbPtr)
	val changes: Int get() = sqlite3_changes(dbPtr)
	val totalChanges: Int get() = sqlite3_total_changes(dbPtr)

	fun execute(command: String, callback: ((Array<String>, Array<String>)-> Int)? = null) {
		memScoped {
			val error = alloc<CPointerVar<ByteVar>>()
			try {
				val result = if (callback == null) {
					sqlite3_exec(dbPtr, command, null, null, error.ptr)
				} else {
					val callbackStable = StableRef.create(callback)
					try {
						sqlite3_exec(
								dbPtr, command,
								staticCFunction { ptr, count, data, columns ->
									val callbackFunction = ptr!!.asStableRef<(Array<String>, Array<String>) -> Int>().get()
									val columnsArray = Array(count) { columns!![it]!!.toKString() }
									val dataArray = Array(count) { data!![it]!!.toKString() }
									callbackFunction(columnsArray, dataArray)
								},
								callbackStable.asCPointer(), error.ptr
						)
					} finally {
						callbackStable.dispose()
					}
				}
				if (result != 0) throw SQLiteError("DB error: ${error.value!!.toKString()}")
			} finally {
				sqlite3_free(error.value)
			}
		}
	}

	fun prepare(sql: String) : Pair<SQLiteStatement, String?> = memScoped {
		val tailPtr = alloc<CPointerVar<ByteVar>>()
		val stmtPtr = alloc<CPointerVar<sqlite3_stmt>>()
		val result = sqlite3_prepare_v3(dbPtr, sql, sql.length, 0, stmtPtr.ptr, tailPtr.ptr)
		if (result != SQLITE_OK) {
			throw SQLiteError("Cannot prepare statement: ${sqlite3_errstr(result)?.toKString()}")
		}
		SQLiteStatement(stmtPtr.value!!) to tailPtr.value?.toKString()
	}

	fun createFunction(name: String, nArg: Int, function: (SQLiteValues, SQLiteContext) -> Unit) {
		val result = sqlite3_create_function_v2(
			dbPtr, name, nArg, SQLITE_UTF8, StableRef.create(function).asCPointer(),
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
			dbPtr, function.name, function.argumentCount, SQLITE_UTF8, StableRef.create(function).asCPointer(),
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
			dbPtr, function.name, function.argumentCount, SQLITE_UTF8, StableRef.create(function).asCPointer(),
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
			if (module is SQLiteModulePersist) { // If not eponymous.
				rawModule.xCreate = staticCFunction { dbPtr, pAux, argc, argv, ppVTab, pzErr ->
					val moduleObj = pAux!!.asStableRef<SQLiteModulePersist>().get()
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

				val bestIndexInfo = virtualTable.bestIndex(
					Array(indexInfo.nConstraint) { SQLiteIndexConstraint(indexInfo.aConstraint!![it].ptr) },
					Array(indexInfo.nOrderBy) { SQLiteIndexOrderBy(indexInfo.aOrderBy!![it].ptr) },
					Array(indexInfo.nConstraint) { SQLiteIndexConstraintUsage(indexInfo.aConstraintUsage!![it].ptr) }
				)

				indexInfo.idxNum = bestIndexInfo.idxNum
				if (bestIndexInfo.idxStr != null) {
					indexInfo.idxStr = sqlite3_mprintf(bestIndexInfo.idxStr)
					indexInfo.needToFreeIdxStr = 1
				}

				indexInfo.orderByConsumed = if (bestIndexInfo.orderByConsumed) 1 else 0
				indexInfo.estimatedCost = bestIndexInfo.estimatedCost
				indexInfo.estimatedRows = bestIndexInfo.estimatedRows
				indexInfo.idxFlags = bestIndexInfo.idxFlags
				indexInfo.colUsed = bestIndexInfo.columnsUsed

				SQLITE_OK
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
				pCursor!!.reinterpret<ksqlite_vtab_cursor>().pointed
					.userObj!!.asStableRef<SQLiteVirtualTableCursor>().get()
					.filter(idxNum, idxStr?.toKString(), SQLiteValues(argv!!, argc))
				SQLITE_OK
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
					dbPtr, name, rawModule.ptr, StableRef.create(module).asCPointer(),
					staticCFunction { ptr -> ptr!!.asStableRef<SQLiteModule>().dispose() }
			)
		}
		check(result == SQLITE_OK) { "Failed to create module." }
	}

	override fun toString(): String = "SQLiteDatabase database in $fileName"

	fun close() {
		sqlite3_close_v2(dbPtr)
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

inline fun withSqlite(path: String, function: (SQLiteDatabase) -> Unit) {
	val db = SQLiteDatabase.open(path)
	try {
		function(db)
	} finally {
		db.close()
	}
}


var SQLiteDatabase.busyTimeout: Long
	get() = withStmt("PRAGMA busy_timeout;") { it.getColumnLong(0) }
	set(value) { sqlite3_busy_timeout(dbPtr, value.toInt()) }

/**
 * The user-version is an integer that is available to applications to use however they want.
 * SQLite makes no use of the user-version itself.
 *
 * It is usually used to keep track of migrations.
 * It's initial value is 0.
 */
var SQLiteDatabase.userVersion: Long
	get() = withStmt("PRAGMA user_version;") { it.getColumnLong(0) }
	set(value) { execute("PRAGMA user_version = $value") }

/**
 * Query, set, or clear the enforcement of foreign key constraints.
 * 
 * This pragma is a no-op within a transaction;
 * foreign key constraint enforcement may only be enabled or disabled when there is no pending BEGIN or SAVEPOINT.
 */
var SQLiteDatabase.foreignKeysEnabled: Boolean
	get() = withStmt("PRAGMA foreign_keys;") { it.getColumnInt(0) != 0 }
	set(value) { execute("PRAGMA foreign_keys = $value;") }
