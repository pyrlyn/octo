package octo.core.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import octo.core.flags.FeatureFlags

/** User settings from `config.json` in the Octo home. */
@Serializable
data class OctoConfig(
    val flags: Map<String, Boolean> = emptyMap(),
) {
    fun featureFlags(): FeatureFlags = FeatureFlags(flags)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** A broken file must not stop Octo from starting, so it falls back to defaults and says why. */
        fun parse(text: String?): ParsedConfig =
            if (text.isNullOrBlank()) {
                ParsedConfig(OctoConfig(), warning = null)
            } else {
                try {
                    ParsedConfig(json.decodeFromString(serializer(), text), warning = null)
                } catch (e: SerializationException) {
                    ParsedConfig(OctoConfig(), warning = "Ignoring invalid config.json: ${e.message}")
                } catch (e: IllegalArgumentException) {
                    ParsedConfig(OctoConfig(), warning = "Ignoring invalid config.json: ${e.message}")
                }
            }
    }
}

data class ParsedConfig(
    val config: OctoConfig,
    val warning: String?,
)
