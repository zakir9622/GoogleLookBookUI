package com.zakir.vestra.shared.engine

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProOrtFailureTest {

    @Test
    fun detectsFp16TypeMismatch() {
        assertTrue(
            ProOrtFailure.isPackIncompatible(
                "Load model from /storage/emulated/0/Documents/TheLookbook/packs/pro-v1/1/unet.onnx failed:" +
                    "Type Error: Type (tensor(float16)) of output arg (_to_copy) of node (node__to_copy) " +
                    "does not match expected type (tensor(float)).",
            ),
        )
    }

    @Test
    fun detectsInvalidControlNetGraph() {
        assertTrue(
            ProOrtFailure.isPackIncompatible(
                "Invalid model. Node input 'node_Conv_736' is not a graph input, initializer, or output of a previous node.",
            ),
        )
    }

    @Test
    fun ignoresUnrelatedFailures() {
        assertFalse(ProOrtFailure.isPackIncompatible("Couldn't read the selected images"))
        assertFalse(ProOrtFailure.isPackIncompatible(null))
        assertFalse(ProOrtFailure.isPackIncompatible(""))
    }
}
