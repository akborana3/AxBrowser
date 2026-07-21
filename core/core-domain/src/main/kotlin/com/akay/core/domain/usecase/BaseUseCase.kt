package com.akay.core.domain.usecase

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

abstract class BaseUseCase<in Input, Output> {
    abstract suspend fun execute(input: Input): Output

    suspend operator fun invoke(input: Input): Output = withContext(Dispatchers.IO) {
        execute(input)
    }
}

abstract class BaseFlowUseCase<in Input, Output> {
    abstract fun execute(input: Input): Flow<Output>

    operator fun invoke(input: Input): Flow<Output> = execute(input)
}

abstract class BaseNoInputUseCase<Output> {
    abstract suspend fun execute(): Output

    suspend operator fun invoke(): Output = withContext(Dispatchers.IO) {
        execute()
    }
}

abstract class BaseFlowNoInputUseCase<Output> {
    abstract fun execute(): Flow<Output>

    operator fun invoke(): Flow<Output> = execute()
}
