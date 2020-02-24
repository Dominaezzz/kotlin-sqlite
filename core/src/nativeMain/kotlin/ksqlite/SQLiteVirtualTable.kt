package ksqlite

import sqlite3.sqlite3_value_nochange

interface SQLiteVirtualTable {
	val declaration: String

	/**
	 * SQLite uses the xBestIndex method of a virtual table module to determine the best way to access the virtual table.
	 * The SQLite core communicates with the xBestIndex method by filling in certain fields of the
	 * sqlite3_index_info structure and passing a pointer to that structure into xBestIndex as the second parameter.
	 * The xBestIndex method fills out other fields of this structure which forms the reply.
	 *
	 * The SQLite core calls the xBestIndex method when it is compiling a query that involves a virtual table.
	 * In other words, SQLite calls this method when it is running sqlite3_prepare() or the equivalent.
	 * By calling this method, the SQLite core is saying to the virtual table that it needs to access some subset of
	 * the rows in the virtual table and it wants to know the most efficient way to do that access.
	 * The xBestIndex method replies with information that the SQLite core can then use to conduct an efficient search of the virtual table.
	 *
	 * While compiling a single SQL query, the SQLite core might call xBestIndex multiple times with different settings in sqlite3_index_info.
	 * The SQLite core will then select the combination that appears to give the best performance.
	 *
	 * Before calling this method, the SQLite core initializes an instance of the sqlite3_index_info structure with information about the query that it is currently trying to process.
	 * This information derives mainly from the WHERE clause and ORDER BY or GROUP BY clauses of the query, but also from any ON or USING clauses if the query is a join.
	 * The information that the SQLite core provides to the xBestIndex method is held in the part of the structure that is marked as "Inputs".
	 * The "Outputs" section is initialized to zero.
	 *
	 * The information in the sqlite3_index_info structure is ephemeral and may be overwritten or deallocated as soon as the xBestIndex method returns.
	 * If the xBestIndex method needs to remember any part of the sqlite3_index_info structure, it should make a copy.
	 * Care must be take to store the copy in a place where it will be deallocated, such as in the idxStr field with needToFreeIdxStr set to 1.
	 *
	 * Note that xBestIndex will always be called before xFilter, since the idxNum and idxStr outputs from xBestIndex are required inputs to xFilter.
	 * However, there is no guarantee that xFilter will be called following a successful xBestIndex.
	 *
	 * ## Inputs
	 * The main thing that the SQLite core is trying to communicate to the virtual table is the constraints that are available to limit the number of rows that need to be searched.
	 * The aConstraint[] array contains one entry for each constraint.
	 * There will be exactly nConstraint entries in that array.
	 *
	 * Each constraint will usually correspond to a term in the WHERE clause or in a USING or ON clause that is of the form
	 *
	 * column OP EXPR
	 * Where "column" is a column in the virtual table, OP is an operator like "=" or "<", and EXPR is an arbitrary expression.
	 * So, for example, if the WHERE clause contained a term like this:
	 *
	 * a = 5
	 * Then one of the constraints would be on the "a" column with operator "=" and an expression of "5". Constraints need not have a literal representation of the WHERE clause. The query optimizer might make transformations to the WHERE clause in order to extract as many constraints as it can. So, for example, if the WHERE clause contained something like this:
	 *
	 * x BETWEEN 10 AND 100 AND 999>y
	 * The query optimizer might translate this into three separate constraints:
	 *
	 * x >= 10
	 * x <= 100
	 * y < 999
	 * For such each constraint, the aConstraint[].iColumn field indicates which column appears on the left-hand side of the constraint.
	 * The first column of the virtual table is column 0.
	 * The rowid of the virtual table is column -1.
	 * The aConstraint[].op field indicates which operator is used.
	 * The SQLITE_INDEX_CONSTRAINT_* constants map integer constants into operator values.
	 * Columns occur in the order they were defined by the call to sqlite3_declare_vtab() in the xCreate or xConnect method.
	 * Hidden columns are counted when determining the column index.
	 *
	 * If the xFindFunction() method for the virtual table is defined, and if xFindFunction() sometimes returns SQLITE_INDEX_CONSTRAINT_FUNCTION or larger, then the constraints might also be of the form:
	 *
	 * FUNCTION( column, EXPR)
	 * In this case the aConstraint[].op value is the same as the value returned by xFindFunction() for FUNCTION.
	 *
	 * The aConstraint[] array contains information about all constraints that apply to the virtual table.
	 * But some of the constraints might not be usable because of the way tables are ordered in a join.
	 * The xBestIndex method must therefore only consider constraints that have an aConstraint[].usable flag which is true.
	 *
	 * In addition to WHERE clause constraints, the SQLite core also tells the xBestIndex method about the ORDER BY clause.
	 * (In an aggregate query, the SQLite core might put in GROUP BY clause information in place of the ORDER BY clause information,
	 * but this fact should not make any difference to the xBestIndex method.)
	 * If all terms of the ORDER BY clause are columns in the virtual table, then nOrderBy will be the number of terms in the ORDER BY clause and
	 * the aOrderBy[] array will identify the column for each term in the order by clause and whether or not that column is ASC or DESC.
	 *
	 * In SQLite version 3.10.0 (2016-01-06) and later, the colUsed field is available to indicate which fields of the virtual table are actually used by the statement being prepared.
	 * If the lowest bit of colUsed is set, that means that the first column is used.
	 * The second lowest bit corresponds to the second column.
	 * And so forth.
	 * If the most significant bit of colUsed is set, that means that one or more columns other than the first 63 columns are used.
	 * If column usage information is needed by the xFilter method, then the required bits must be encoded into either the idxNum or idxStr output fields.
	 *
	 * ## Outputs
	 * Given all of the information above, the job of the xBestIndex method it to figure out the best way to search the virtual table.
	 *
	 * The xBestIndex method fills the idxNum and idxStr fields with information that communicates an indexing strategy to the xFilter method.
	 * The information in idxNum and idxStr is arbitrary as far as the SQLite core is concerned.
	 * The SQLite core just copies the information through to the xFilter method.
	 * Any desired meaning can be assigned to idxNum and idxStr as long as xBestIndex and xFilter agree on what that meaning is.
	 *
	 * The idxStr value may be a string obtained from an SQLite memory allocation function such as sqlite3_mprintf().
	 * If this is the case, then the needToFreeIdxStr flag must be set to true so that the SQLite core will know to call sqlite3_free() on that string when it has finished with it, and thus avoid a memory leak.
	 *
	 * If the virtual table will output rows in the order specified by the ORDER BY clause, then the orderByConsumed flag may be set to true.
	 * If the output is not automatically in the correct order then orderByConsumed must be left in its default false setting.
	 * This will indicate to the SQLite core that it will need to do a separate sorting pass over the data after it comes out of the virtual table.
	 *
	 * The estimatedCost field should be set to the estimated number of disk access operations required to execute this query against the virtual table.
	 * The SQLite core will often call xBestIndex multiple times with different constraints, obtain multiple cost estimates, then choose the query plan that gives the lowest estimate.
	 *
	 * If the current version of SQLite is 3.8.2 or greater, the estimatedRows field may be set to an estimate of the number of rows returned by the proposed query plan.
	 * If this value is not explicitly set, the default estimate of 25 rows is used.
	 *
	 * If the current version of SQLite is 3.9.0 or greater, the idxFlags field may be set to SQLITE_INDEX_SCAN_UNIQUE to indicate that the virtual table will return only zero or one rows given the input constraints.
	 * Additional bits of the idxFlags field might be understood in later versions of SQLite.
	 *
	 * The aConstraintUsage[] array contains one element for each of the nConstraint constraints in the inputs section of the sqlite3_index_info structure.
	 * The aConstraintUsage[] array is used by xBestIndex to tell the core how it is using the constraints.
	 *
	 * The xBestIndex method may set aConstraintUsage[].argvIndex entries to values greater than zero.
	 * Exactly one entry should be set to 1, another to 2, another to 3, and so forth up to as many or as few as the xBestIndex method wants.
	 * The EXPR of the corresponding constraints will then be passed in as the argv[] parameters to xFilter.
	 *
	 * For example, if the aConstraint[3].argvIndex is set to 1, then when xFilter is called, the argv[0] passed to xFilter will have the EXPR value of the aConstraint[3] constraint.
	 *
	 * By default, the SQLite core double checks all constraints on each row of the virtual table that it receives.
	 * If such a check is redundant, the xBestFilter method can suppress that double-check by setting aConstraintUsage[].omit.
	 *
	 * @param[constraints] Table of WHERE clause constraints.
	 * */
	fun bestIndex(constraints: Array<Constraint>, orderBys: Array<OrderBy>, constraintUsages: Array<ConstraintUsage>, info: SQLiteIndexInfo)
	/**
	 * This method creates a new cursor used for accessing (read and/or writing) a virtual table.
	 * A successful invocation of this method will return an instance of [SQLiteVirtualTableCursor].
	 *
	 * For every successful call to this method, the SQLite core will later invoke [SQLiteVirtualTableCursor.close] to destroy the cursor instance.
	 * A virtual table implementation must be able to support an arbitrary number of simultaneously open cursors.
	 * When initially opened, the cursor is in an undefined state.
	 * The SQLite core will invoke [SQLiteVirtualTableCursor.filter] on the cursor prior to any attempt to position or read from the cursor.
	 * */
	fun open(): SQLiteVirtualTableCursor
	/**
	 * This method releases a connection to a virtual table. Only the sqlite3_vtab object is destroyed.
	 * The virtual table is not destroyed and any backing store associated with the virtual table persists.
	 * This method undoes the work of [SQLiteModule.connect].
	 * This method is a destructor for a connection to the virtual table.
	 * Contrast this method with [Persist.destroy]. [Persist.destroy] is a destructor for the entire virtual table.
	 * */
	fun disconnect() {}

	fun findFunction(nArg: Int, name: String): SQLiteFunction? = null

	// If not used then read-only.
	interface Persist : SQLiteVirtualTable {
		fun rename(newName: String): Boolean = false
		fun update(args: SQLiteValues): Long = -1
		/**
		 * This method releases a connection to a virtual table, just like the xDisconnect method,
		 * and it also destroys the underlying table implementation.
		 * This method undoes the work of [SQLiteModule.Persist.create].
		 * [disconnect] is called whenever a database connection that uses a virtual table is closed.
		 * [destroy] is only called when a DROP TABLE statement is executed against the virtual table.
		 * */
		fun destroy()

		/**
		 * Within the [update] method of a virtual table, this property returns true if and only if the column corresponding
		 * to the receiver is unchanged by the UPDATE operation that the [update] method call
		 * was invoked to implement and if and the prior [SQLiteVirtualTableCursor.column] call that was invoked to extracted the value for that column
		 * returned without setting a result (probably because it queried [SQLiteVirtualTableCursor.noChange] and found that the column was unchanging).
		 * Within an [update] method, any value for which [noChange] is true will in all other respects appear to be a NULL value.
		 * If [noChange] is invoked anywhere other than within an [update] method call for an UPDATE statement,
		 * then the return value is arbitrary and meaningless.
		 **/
		val SQLiteValue.noChange: Boolean get() = sqlite3_value_nochange(ptr) != 0
	}

	interface Transactions : SQLiteVirtualTable {
		fun begin()
		fun commit()
		fun rollback()

		fun sync()

		interface Nested : Transactions {
			fun savePoint(n: Int)
			fun release(n: Int)
			fun rollbackTo(n: Int)
		}
	}
}
