import ksqlite.*
import kotlin.test.*

@Test
fun `But... does it work?`() {
	usingSqlite(":memory:") { db ->
		db.execute("SELECT 'It works!';") { _, data ->
			assertEquals(data[0], "It works!", "Uh oh")
			0
		}
	}
}

@Test
fun `Store and load BLOB`() {
	usingSqlite(":memory:") { db ->
		db.usingStmt("SELECT ?;") { stmt ->
			val expected = byteArrayOf(1, 2, 3, 0, 6, 5, 4)

			stmt.bind(1, expected)
			stmt.step()

			val actual = stmt.getColumnBlob(0)

			assertTrue(expected contentEquals actual!!, "BLOB values should be consistent.")
		}
	}
}

@Test
fun `Custom Scalar Function`() {
	val lol = object : SQLiteFunction() {
		override operator fun invoke(values: SQLiteValues, context: SQLiteContext) {
			context.setResult("The Custom Function works!!")
		}
	}

	usingSqlite(":memory:") { db ->
		db.createFunction("LOL", 0, lol)

		lateinit var output: String

		db.execute("SELECT LOL();") { _, data ->
			output = data[0]
			0
		}

		assertEquals("The Custom Function works!!", output, "Custom function should return correct value.")
	}
}

@Test
fun `Custom Aggregate Function`() {
	data class LOLContext(var product: Int)

	val lol = object : SQLiteFunction.Aggregate() {
		override fun step(values: SQLiteValues, context: SQLiteContext) {
			val aggregateContext = context.getAggregateContext { LOLContext(1) }
			aggregateContext.product *= values[0].asInt()
		}

		override fun final(context: SQLiteContext) {
			context.setResult("Product: ${context.getAggregateContext<LOLContext>()?.product}")
		}
	}

	usingSqlite(":memory:") { db ->
		db.createFunction("LOL", 1, lol)

		lateinit var output: String

		db.execute("""
			WITH list(num) AS (
				VALUES (2), (3), (4), (7)
			)
			SELECT LOL(num)
			FROM list;
			""") { _, data ->
			output = data[0]
			0
		}

		assertEquals("Product: ${2 * 3 * 4 * 7}", output, "Custom function should return correct value.")
	}
}

fun initdb(db: SQLiteDatabase) {
	db.execute("DROP TABLE IF EXISTS LOL;")
	db.execute("CREATE TABLE LOL(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, age INTEGER NOT NULL);")
	db.usingStmt("INSERT INTO LOL(name, age) VALUES (?, ?);") { stmt ->
		stmt.bind(1, "Dominic")
		stmt.bind(2, 19)
		stmt.step()

		stmt.reset()

		stmt.bind(1, "Fischer")
		stmt.bind(2, 14)
		stmt.step()

		stmt.reset()

		stmt.bind(1, "Whatever")
		stmt.bind(2, -87)
		stmt.step()

		stmt.reset()

		stmt.bind(1, "Haha!")
		stmt.bind(2, 569)
		stmt.step()
	}
}

fun testExecuteResult(db: SQLiteDatabase) {
	db.execute("SELECT * FROM LOL;") { columns, data ->
		println("Columns: ${columns.joinToString()};   Data: ${data.joinToString()}")
		0
	}
}

//fun testStatementBinding(db: SQLiteDatabase) {
//	db.withStmt("INSERT INTO LOL(name, age) VALUES (?, ?);") { stmt ->
//		println(stmt.sql)
//
//		stmt.bind(1, "Dominic")
//		println(stmt.expandedSql)
//
//		stmt.bind(2, 19)
//		println(stmt.expandedSql)
//
//		stmt.bind(1, "Fischer")
//		println(stmt.expandedSql)
//
//		stmt.bind(2, 40540)
//		println(stmt.expandedSql)
//	}
//}

fun testStatementQuery(db: SQLiteDatabase) {
	db.usingStmt("SELECT * FROM LOL;") { stmt ->
		while (stmt.step()) {
			println("id=${stmt.getColumnInt(0)}, name=${stmt.getColumnString(1)}, age=${stmt.getColumnLong(2)};")
		}
	}
}

fun main(args: Array<String>) {
	usingSqlite("temp.db") { db ->
		println("SQLite Version: ${SQLiteDatabase.version}")

		initdb(db)

		// testStatementBinding(dbPtr)
		// testExecuteResult(dbPtr)
		// testStatementQuery(dbPtr)
	}
}
