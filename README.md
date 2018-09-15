# kotlin-sqlite

[Kotlin/Native](https://github.com/JetBrains/kotlin-native) bindings to the
[sqlite](https://www.sqlite.org) C library.

SQLite is an in-process library that implements a self-contained, serverless, zero-configuration, transactional SQL database engine. The code for SQLite is in the public domain and is thus free for use for any purpose, commercial or private.

This library allows for 'kotlinified' use of SQLite and is based on the kotlinconf-spinner [SQLite example](https://github.com/JetBrains/kotlinconf-spinner/tree/master/kotlin-native/samples/fullstack/sql).

## Examples

```Kotlin
withSqlite("dbPtr") { db ->
    println("SQLite Version: ${db.version}")

    db.execute("""
        CREATE TABLE LOL(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            age INTEGER NOT NULL
        );
        """)

    db.withStmt("INSERT INTO LOL(name, age) VALUES (?, ?);") { stmt ->
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

    db.execute("SELECT * FROM LOL;") { columns, data ->
        println("Columns: ${columns.joinToString()};   Data: {data.joinToString()}")
        0
    }

    db.withStmt("SELECT * FROM LOL;") { stmt ->
        while (stmt.step()) {
            println("id=${stmt.getColumnInt(0)}, name=${stmt.getColumnString(1)}, age=${stmt.getColumnLong(2)};")
        }
    }

    val count = db.withStmt("SELECT COUNT(*) FROM LOL;") { it.getColumnInt(0) }
}
```

Will have to look at the unit tests for more examples.

## Notes

* Only tested on WSL and Windows but should work on Linux and Mac.

* The bindings are not final, nicely wrapping SQLite's C API is a WIP.

* Contributions are welcome.
