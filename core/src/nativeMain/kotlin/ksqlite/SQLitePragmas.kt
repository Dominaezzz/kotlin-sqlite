package ksqlite

import sqlite3.sqlite3_busy_timeout


var SQLiteDatabase.busyTimeout: Long
	get() = withStmt("PRAGMA busy_timeout;") { getColumnLong(0) }
	set(value) { sqlite3_busy_timeout(ptr, value.toInt()) }

/**
 * The user-version is an integer that is available to applications to use however they want.
 * SQLite makes no use of the user-version itself.
 *
 * It is usually used to keep track of migrations.
 * It's initial value is 0.
 */
var SQLiteDatabase.userVersion: Long
	get() = withStmt("PRAGMA user_version;") { getColumnLong(0) }
	set(value) { execute("PRAGMA user_version = $value") }

/**
 * Query, set, or clear the enforcement of foreign key constraints.
 *
 * This pragma is a no-op within a transaction;
 * foreign key constraint enforcement may only be enabled or disabled when there is no pending BEGIN or SAVEPOINT.
 */
var SQLiteDatabase.foreignKeysEnabled: Boolean
	get() = withStmt("PRAGMA foreign_keys;") { getColumnInt(0) != 0 }
	set(value) { execute("PRAGMA foreign_keys = $value;") }
