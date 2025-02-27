/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.bridge.internal

import com.datadog.android.bridge.DdTrace
import com.datadog.android.tracing.AndroidTracer
import io.opentracing.Span
import io.opentracing.Tracer
import java.util.concurrent.TimeUnit

internal class BridgeTrace(
    private val tracerProvider: () -> Tracer = { AndroidTracer.Builder().build() }
) : DdTrace {

    private val spanMap: MutableMap<String, Span> = mutableMapOf()

    // lazy here is on purpose. The thing is that this class will be instantiated even
    // before Sdk.initialize is called, but Tracer can be created only after SDK is initialized.
    private val tracer by lazy { tracerProvider.invoke() }

    override fun startSpan(
        operation: String,
        context: Map<String, Any?>,
        timestampMs: Long
    ): String {
        val builtSpan = tracer.buildSpan(operation)
            .withStartTimestamp(TimeUnit.MILLISECONDS.toMicros(timestampMs))
        val parent = associateChildren(context)
        if (parent != null) {
            builtSpan.asChildOf(parent)
        }
        val span = builtSpan.start()
        val spanContext = span.context()
        span.setTags(context)
        val id = retrieveId(context)
        if (id != null) {
            span.setBaggageItem("id", id)
        }
        span.setTags(GlobalState.globalAttributes)
//        TODO: set this to the passed id?
        val spanId = spanContext.toSpanId()
        spanMap[spanId] = span
        return spanId
    }

    override fun finishSpan(
        spanId: String,
        context: Map<String, Any?>,
        timestampMs: Long
    ) {
        val span = spanMap.remove(spanId) ?: return
        span.setTags(context)
        span.setTags(GlobalState.globalAttributes)
        span.finish(TimeUnit.MILLISECONDS.toMicros(timestampMs))
    }

    private fun Span.setTags(tags: Map<String, Any?>) {
        tags.forEach { (key, value) ->
            when (value) {
                is Boolean -> setTag(key, value)
                is Number -> setTag(key, value)
                is String -> setTag(key, value)
                else -> setTag(key, value?.toString())
            }
        }
    }

    private fun associateChildren(context: Map<String, Any?>): Span? {
        if (context.containsKey("childOf")) {
            val parent = spanMap[context["childOf"]]
            if (parent != null) {
                return parent
            }
        }
        return null
    }

    private fun retrieveId(context: Map<String, Any?>): String? {
        if (context.containsKey("view.id")) {
            val id = context["view.id"] as String?
            if (id !== null) {
                return id
            }
        }
        return null
    }
}
