package com.zakir.vestra.shared.cloud

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GradioSchemaClientTest {
    private val client = GradioSchemaClient(http = io.ktor.client.HttpClient())

    @Test
    fun parseInfoNamedEndpoints() {
        val raw = """
            {
              "named_endpoints": {
                "/infer": {
                  "parameters": [
                    {"parameter_name": "prompt", "type": "str", "parameter_default": ""},
                    {"parameter_name": "seed", "type": "number", "parameter_default": 0},
                    {"parameter_name": "steps", "type": "number", "parameter_default": 4}
                  ]
                }
              }
            }
        """.trimIndent()
        val parsed = client.parseInfo(raw)
        assertTrue(parsed.containsKey("/infer"))
        assertEquals(3, parsed["/infer"]!!.size)
        assertEquals("prompt", parsed["/infer"]!![0].name)
    }
}
