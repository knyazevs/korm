package s.knyazev

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
abstract class Entity (
    @Transient
    open var fields: MutableMap<String, Any?> = mutableMapOf(),
)