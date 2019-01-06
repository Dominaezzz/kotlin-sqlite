import ksqlite.*
import kotlin.test.*

object StringSplitter : SQLiteModule {
	override fun connect(db: SQLiteDatabase, args: Array<String>) = SplitResult

	object SplitResult : SQLiteVirtualTable {
		override val declaration : String
			get() = "CREATE TABLE x(value, input hidden, delimiter hidden)"

		override fun open() : SQLiteVirtualTableCursor = SplitResultCursor()

		override fun bestIndex(constraints: Array<Constraint>, orderBys: Array<OrderBy>, constraintUsages: Array<ConstraintUsage>, info: SQLiteIndexInfo) {
			constraintUsages[0].argvIndex = 1
			constraintUsages[0].omit = true
			constraintUsages[1].argvIndex = 2
			constraintUsages[1].omit = true

			info.indexNumber = 0
			info.idxStr = null
			info.orderByConsumed = true
			info.estimatedCost = 0.0
			info.estimatedRows = 10
//			info.idxFlags = 0
//			info.columnsUsed = 2U
		}

		class SplitResultCursor : SQLiteVirtualTableCursor {
			lateinit var input: String
			lateinit var delimiter: String
			lateinit var results: List<String>
			override var rowId: Long = 0
			override val eof: Boolean
				get() = rowId >= results.size

			override fun filter(idxNum: Int, idxStr: String?, args: SQLiteValues) {
				if (args.size != 2) throw Exception("Invalid")

				input = args[0].asString()!!
				delimiter = args[1].asString()!!

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
		}
	}
}

@Test
fun `Custom Table Valued Function`() {
	withSqlite(":memory:") { db ->
		db.createModule("split_string", StringSplitter)

		db.withStmt("SELECT value FROM split_string('Mine,Is,Now,Separated', ',');") { stmt ->
			assert(stmt.step())
			assertEquals("Mine", stmt.getColumnString(0))
			assert(stmt.step())
			assertEquals("Is", stmt.getColumnString(0))
			assert(stmt.step())
			assertEquals("Now", stmt.getColumnString(0))
			assert(stmt.step())
			assertEquals("Separated", stmt.getColumnString(0))
			assert(!stmt.step())
		}

		db.withStmt("""
			SELECT t1.value, t2.value
			FROM split_string('X.Y.Z', '.') AS t1
			JOIN split_string('A-B-C', '-') AS t2;
			""") { stmt ->
			assert(stmt.step())
			assertEquals("X", stmt.getColumnString(0))
			assertEquals("A", stmt.getColumnString(1))
			assert(stmt.step())
			assertEquals("X", stmt.getColumnString(0))
			assertEquals("B", stmt.getColumnString(1))
			assert(stmt.step())
			assertEquals("X", stmt.getColumnString(0))
			assertEquals("C", stmt.getColumnString(1))
			assert(stmt.step())
			assertEquals("Y", stmt.getColumnString(0))
			assertEquals("A", stmt.getColumnString(1))
			assert(stmt.step())
			assertEquals("Y", stmt.getColumnString(0))
			assertEquals("B", stmt.getColumnString(1))
			assert(stmt.step())
			assertEquals("Y", stmt.getColumnString(0))
			assertEquals("C", stmt.getColumnString(1))
			assert(stmt.step())
			assertEquals("Z", stmt.getColumnString(0))
			assertEquals("A", stmt.getColumnString(1))
			assert(stmt.step())
			assertEquals("Z", stmt.getColumnString(0))
			assertEquals("B", stmt.getColumnString(1))
			assert(stmt.step())
			assertEquals("Z", stmt.getColumnString(0))
			assertEquals("C", stmt.getColumnString(1))
			assert(!stmt.step())
		}
	}
}