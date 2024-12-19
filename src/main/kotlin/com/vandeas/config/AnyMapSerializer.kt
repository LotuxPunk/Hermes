package com.vandeas.config

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

object AnyMapSerializer : KSerializer<Map<String, Any?>> by MapSerializer(String.serializer(), AnySerializer)
