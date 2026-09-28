package io.github.hansanto.vaulttools.search.render

import io.github.hansanto.vaulttools.search.service.VaultSearchResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object JsonRender : Render {

    private val format = Json {
        prettyPrint = true
    }

    override suspend fun renderString(namespace: String?, searchResult: VaultSearchResult): String {
        val (pathsSecretFound, pathsSecretError) = searchResult.partition()

        val jsonRenderResult = JsonRenderResult(
            namespace = namespace,
            totalPathsAnalyzed = searchResult.result.size,
            secretPaths = pathsSecretFound.mapKeys { it.key.value }.mapValues { it.value.lines },
            errorPaths = pathsSecretError.mapKeys {
                it.key.value
            }.mapValues { RendererUtil.getErrorMessage(it.value.error) }
        )

        return format.encodeToString(jsonRenderResult)
    }
}

@Serializable
data class JsonRenderResult(
    val namespace: String?,
    val totalPathsAnalyzed: Int,
    val secretPaths: Map<String, Collection<Int>>,
    val errorPaths: Map<String, String>
)
