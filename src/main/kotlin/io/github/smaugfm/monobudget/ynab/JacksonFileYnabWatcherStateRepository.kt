package io.github.smaugfm.monobudget.ynab

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class JacksonFileYnabWatcherStateRepository(
    private val path: Path,
) : YnabWatcherStateRepository {
    private val objectMapper =
        jsonMapper {
            enable(SerializationFeature.INDENT_OUTPUT)
            addModule(kotlinModule())
        }

    override suspend fun load(budgetId: String): YnabWatcherState? {
        if (!path.exists()) {
            return null
        }
        val states = objectMapper.readValue<List<YnabWatcherState>>(path.readText())
        return states.find { it.budgetId == budgetId }
    }

    override suspend fun save(state: YnabWatcherState) {
        val states =
            if (path.exists()) {
                objectMapper.readValue<List<YnabWatcherState>>(path.readText())
                    .filterNot { it.budgetId == state.budgetId }
            } else {
                emptyList()
            }
        path.writeText(
            objectMapper.writeValueAsString(states + state),
            Charsets.UTF_8,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.CREATE,
        )
    }
}
