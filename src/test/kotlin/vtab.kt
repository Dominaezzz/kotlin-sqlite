import ksqlite.*
import kotlin.test.*

object StringSplitter : SQLiteModule() {
    override fun connect(db: SQLiteDatabase, args: Array<String>) : SQLiteVirtualTable {
        println("CONNECT() CALLED WITH(${args.contentToString()})")
        return SplitResult()
    }

    class SplitResult : SQLiteVirtualTable {
        override val declaration : String
            get() = "CREATE TABLE x(value, input hidden, delimiter hidden)"

        override fun open() : SQLiteVirtualTableCursor = SplitResultCursor()

        override fun bestIndex(constraints: Array<SQLiteIndexConstraint>, orderBys: Array<SQLiteIndexOrderBy>, constraintUsages: Array<SQLiteIndexConstraintUsage>): SQLiteIndexInfo {
            constraintUsages[0].argvIndex = 1
            constraintUsages[0].omit = true
            constraintUsages[1].argvIndex = 2
            constraintUsages[1].omit = true
            
            return SQLiteIndexInfo(
                idxNum = 0,
                idxStr = null,
                orderByConsumed = true,
                estimatedCost = 0.0,
                estimatedRows = 10,
                idxFlags = 0,
                columnsUsed = 2
            )
        }

        override fun rename(newName: String) : Boolean { return false }
        override fun update(args: SQLiteValues) : Long { return -1 }
        override fun destroy() {}
        override fun disconnect() {}

        class SplitResultCursor : SQLiteVirtualTableCursor {
            lateinit var input: String
            lateinit var delimiter: String
            lateinit var results: List<String>
            override var rowId: Long = 0
            override val eof: Boolean
                get() = rowId >= results.size

            override fun filter(idxNum: Int, idxStr: String?, args: SQLiteValues) {
                println("FILTER() CALLED ON $this WITH(${args.count}, ${args.getAsString(0)}, ${args.getAsString(1)})")
                if (args.count != 2) throw Exception("Invalid")

                input = args.getAsString(0)!!
                delimiter = args.getAsString(1)!!

                results = input.split(delimiter)
                rowId = 0
            }

            override fun next() { rowId++ }

            override fun column(context: SQLiteContext, columnIndex: Int) {
                when (columnIndex) {
                    0 -> context.setResult(results[rowId.toInt()])
                    1 -> context.setResult(input)
                    2 -> context.setResult(delimiter)
                    // 3 -> context.setResult(table.limit)
                }
            }

            override fun close() {}
        }
    }
}

@Test
fun `Custom Table Valued Function`() {
    withSqlite(":memory:") { db ->
        db.createModule("split_string", StringSplitter)

        db.withStmt("SELECT value FROM split_string('Mine,Is,Now,Seperated', ',');") { stmt ->
            while(stmt.step()) println(stmt.getColumnString(0))
        }

        println("-------------------------------------------------------------")

        db.withStmt("""
            SELECT t1.value, t2.value
            FROM split_string('X.Y.Z', '.') AS t1
            JOIN split_string('A-B-C', '-') AS t2;
            """) { stmt ->
            while(stmt.step()) println("${stmt.getColumnString(0)} ${stmt.getColumnString(1)}")
        }
    }
}