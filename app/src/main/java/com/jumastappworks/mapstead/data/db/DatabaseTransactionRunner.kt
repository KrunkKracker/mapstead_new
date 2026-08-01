package com.jumastappworks.mapstead.data.db

import androidx.room.withTransaction
import javax.inject.Inject

interface DatabaseTransactionRunner {
    suspend fun <T> run(block: suspend () -> T): T
}

class RoomDatabaseTransactionRunner @Inject constructor(
    private val database: MapsteadDatabase
) : DatabaseTransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T =
        database.withTransaction(block)
}
