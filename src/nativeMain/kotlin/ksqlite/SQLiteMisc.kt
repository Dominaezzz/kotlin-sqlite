package ksqlite

import kotlinx.cinterop.*
import sqlite3.*


/**
 * Note the warnings on the "estimatedRows", "idxFlags", and colUsed fields.
 * These fields were added with SQLite versions 3.8.2, 3.9.0, and 3.10.0, respectively.
 * Any extension that reads or writes these fields must first check that the version of the SQLite library in use is greater than or equal to appropriate version - perhaps comparing the value returned from sqlite3_libversion_number() against constants 3008002, 3009000, and/or 3010000.
 * The result of attempting to access these fields in an sqlite3_index_info structure created by an older version of SQLite are undefined.
 * */
class SQLiteIndexInfo(private val ptr: CPointer<sqlite3_index_info>) {
    /** Number used to identify the index */
    var indexNumber: Int
        get() = ptr.pointed.idxNum
        set(value) { ptr.pointed.idxNum = value }

    /***/
    var idxStr: String?
        get() = ptr.pointed.idxStr?.toKString()
        set(value) {
            if (ptr.pointed.needToFreeIdxStr == 1) {
                sqlite3_free(ptr.pointed.idxStr)
            }
            ptr.pointed.idxStr = value?.let { sqlite3_mprintf(it) }
            ptr.pointed.needToFreeIdxStr = if (value != null) 1 else 0
        }

    /** True if output is already ordered */
    var orderByConsumed: Boolean
        get() = ptr.pointed.orderByConsumed == 1
        set(value) { ptr.pointed.orderByConsumed = if (value) 1 else 0 }

    /** Estimated cost of using this index */
    var estimatedCost: Double
        get() = ptr.pointed.estimatedCost
        set(value) { ptr.pointed.estimatedCost = value }

    /** Estimated number of rows returned. (Only available in SQLite 3.8.2 and later) */
    var estimatedRows: Long
        get() = ptr.pointed.estimatedRows
        set(value) { ptr.pointed.estimatedRows = value }

    /** Mask of SQLITE_INDEX_SCAN_* flags. (Only available in SQLite 3.9.0 and later) */
    var idxFlags: Int
        get() = ptr.pointed.idxFlags
        set(value) { ptr.pointed.idxFlags = value }

    /** Input: Mask of columns used by statement. (Only available in SQLite 3.10.0 and later) */
    var columnsUsed: ULong
        get() = ptr.pointed.colUsed
        set(value) { ptr.pointed.colUsed = value}
}

enum class ConstraintOp(val value: UByte) {
    EQ(SQLITE_INDEX_CONSTRAINT_EQ.toUByte()),
    GT(SQLITE_INDEX_CONSTRAINT_GT.toUByte()),
    LE(SQLITE_INDEX_CONSTRAINT_LE.toUByte()),
    LT(SQLITE_INDEX_CONSTRAINT_LT.toUByte()),
    GE(SQLITE_INDEX_CONSTRAINT_GE.toUByte()),
    MATCH(SQLITE_INDEX_CONSTRAINT_MATCH.toUByte()),
    /** 3.10.0 and later */
    LIKE(SQLITE_INDEX_CONSTRAINT_LIKE.toUByte()),

    /** 3.10.0 and later */
    GLOB(SQLITE_INDEX_CONSTRAINT_GLOB.toUByte()),

    /** 3.10.0 and later */
    REGEXP(SQLITE_INDEX_CONSTRAINT_REGEXP.toUByte()),

    /** 3.21.0 and later */
    NE(SQLITE_INDEX_CONSTRAINT_NE.toUByte()),

    /** 3.21.0 and later */
    ISNOT(SQLITE_INDEX_CONSTRAINT_ISNOT.toUByte()),

    /** 3.21.0 and later */
    ISNOTNULL(SQLITE_INDEX_CONSTRAINT_ISNOTNULL.toUByte()),

    /** 3.21.0 and later */
    ISNULL(SQLITE_INDEX_CONSTRAINT_ISNULL.toUByte()),

    /** 3.21.0 and later */
    IS(SQLITE_INDEX_CONSTRAINT_IS.toUByte()),

//		/** 3.25.0 and later */
//		FUNCTION(SQLITE_INDEX_CONSTRAINT_FUNCTION),

    /** Scan visits at most 1 row */
    SCAN_UNIQUE(SQLITE_INDEX_SCAN_UNIQUE.toUByte())
}

inline class Constraint(private val constraint: CPointer<sqlite3_index_constraint>) {
    /** Column constrained.  -1 for ROWID */
    val columnIndex: Int get() = constraint.pointed.iColumn

    /** Constraint operator */
    val op: UByte get() = constraint.pointed.op

    /** True if this constraint is usable */
    val usable: Boolean get() = constraint.pointed.usable != 0.toUByte()
}

inline class OrderBy(private val orderBy: CPointer<sqlite3_index_orderby>) {
    val columnIndex: Int get() = orderBy.pointed.iColumn
    val isDesc: Boolean get() = orderBy.pointed.desc != 0.toUByte()
}

inline class ConstraintUsage(private val constraintUsage: CPointer<sqlite3_index_constraint_usage>) {
    /** if >0, constraint is part of argv to xFilter */
    var argvIndex: Int
        get() = constraintUsage.pointed.argvIndex
        set(value) { constraintUsage.pointed.argvIndex = value }

    /** Do not code a test for this constraint */
    var omit: Boolean
        get() = constraintUsage.pointed.omit != 0.toUByte()
        set(value) { constraintUsage.pointed.omit = value.toByte().toUByte() }
}
