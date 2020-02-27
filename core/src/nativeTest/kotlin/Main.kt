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
fun `Print version string`() {
	println("SQLite Version: ${SQLiteDatabase.version}")
}

@Test
fun `Store and load BLOB`() {
	usingSqlite(":memory:") { db ->
		db.usingStmt("SELECT ?;") { stmt ->
			val expected = byteArrayOf(1, 2, 3, 0, 6, 5, 4)

			stmt.bind(1, expected)
			stmt.step()

			val actual = stmt.getColumnBlob(0)

			assertNotNull(actual)
			assertTrue(expected contentEquals actual, "BLOB values should be consistent.")
		}
	}
}

@Test
fun `Custom Scalar Function`() {
	val factorial = object : SQLiteFunction() {
		override fun invoke(values: SQLiteValues, context: SQLiteContext) {
			val value = values[0].asInt()
			val result = (1..value).fold(1, Int::times)
			context.setResult(result)
		}
	}

	usingSqlite(":memory:") { db ->
		db.createFunction("FACTORIAL", 1, factorial)

		db.withStmt("SELECT FACTORIAL(?);") {
			bind(1, 4)
			step()
			assertEquals(4 * 3 * 2 * 1, getColumnInt(0))
			reset()

			bind(1, 5)
			step()
			assertEquals(5 * 4 * 3 * 2 * 1, getColumnInt(0))
			reset()

			bind(1, 6)
			step()
			assertEquals(6 * 5 * 4 * 3 * 2 * 1, getColumnInt(0))
			reset()
		}
	}
}

@Test
fun `Custom Aggregate Function`() {
	data class Context(var product: Int)

	val product = object : SQLiteFunction.Aggregate() {
		override fun step(values: SQLiteValues, context: SQLiteContext) {
			val aggregateContext = context.getAggregateContext { Context(1) }
			aggregateContext.product *= values[0].asInt()
		}

		override fun final(context: SQLiteContext) {
			context.setResult("Product: ${context.getAggregateContext<Context>()?.product}")
		}
	}

	usingSqlite(":memory:") { db ->
		db.createFunction("PRODUCT", 1, product)

		lateinit var output: String

		db.execute("""
			WITH list(num) AS (
				VALUES (2), (3), (4), (7)
			)
			SELECT PRODUCT(num)
			FROM list;
			""") { _, data ->
			output = data[0]
			0
		}

		assertEquals("Product: ${2 * 3 * 4 * 7}", output, "Custom function should return correct value.")
	}
}
