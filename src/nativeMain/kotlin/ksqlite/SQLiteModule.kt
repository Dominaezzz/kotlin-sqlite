package ksqlite

interface SQLiteModule {
	/** Called to connect to an existing table. */
	fun connect(db: SQLiteDatabase, args: Array<String>) : SQLiteVirtualTable

	interface Persist : SQLiteModule {
		/** Called on CREATE VIRTUAL TABLE.... (Optional) */
		fun create(db: SQLiteDatabase, args: Array<String>) : SQLiteVirtualTable.Persist
	}
}
