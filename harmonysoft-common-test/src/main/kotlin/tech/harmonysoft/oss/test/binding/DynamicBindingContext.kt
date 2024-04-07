package tech.harmonysoft.oss.test.binding

import jakarta.inject.Named
import org.slf4j.LoggerFactory
import tech.harmonysoft.oss.test.util.TestUtil.fail
import java.util.concurrent.ConcurrentHashMap

@Named
class DynamicBindingContext {

    private val bindings = ConcurrentHashMap<DynamicBindingKey, Any?>()
    private val logger = LoggerFactory.getLogger(this::class.java)

    fun hasBindingFor(key: DynamicBindingKey): Boolean {
        return bindings.containsKey(key)
    }

    fun getBinding(key: DynamicBindingKey): Any? {
        return if (hasBindingFor(key)) {
            bindings[key]
        } else {
            fail("no binding is found for key $key, available bindings: $bindings")
        }
    }

    fun storeBinding(key: DynamicBindingKey, value: Any?) {
        bindings[key] = value
        logger.info("stored dynamic binding: {}={}", key, value)
    }

    fun storeBindings(bindings: Map<DynamicBindingKey, Any?>) {
        for ((key, value) in bindings) {
            storeBinding(key, value)
        }
    }
}