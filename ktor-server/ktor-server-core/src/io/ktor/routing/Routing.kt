package io.ktor.routing

import io.ktor.application.*
import io.ktor.pipeline.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.util.*

class Routing(val application: Application) : Route(parent = null, selector = RootRouteSelector) {
    private suspend fun interceptor(context: PipelineContext<Unit, ApplicationCall>) {
        val call = context.call
        val resolveContext = RoutingResolveContext(this, call, call.parameters, call.request.headers)
        val resolveResult = resolveContext.resolve()
        if (resolveResult.succeeded) {
            executeResult(context, resolveResult.entry, resolveResult.values)
        }
    }

    private suspend fun executeResult(context: PipelineContext<Unit, ApplicationCall>, route: Route, parameters: ValuesMap) {
        val routingCallPipeline = route.buildPipeline()
        val receivePipeline = ApplicationReceivePipeline().apply {
            merge(context.call.request.pipeline)
            merge(routingCallPipeline.receivePipeline)
        }
        val responsePipeline = ApplicationSendPipeline().apply {
            merge(context.call.response.pipeline)
            merge(routingCallPipeline.sendPipeline)
        }
        val routingCall = RoutingApplicationCall(context.call, route, receivePipeline, responsePipeline, parameters)
        application.environment.monitor.raise(RoutingCallStarted, routingCall)
        try {
            routingCallPipeline.execute(routingCall)
        } finally {
            application.environment.monitor.raise(RoutingCallFinished, routingCall)
        }
    }

    companion object Feature : ApplicationFeature<Application, Routing, Routing> {

        val RoutingCallStarted = EventDefinition<RoutingApplicationCall>()
        val RoutingCallFinished = EventDefinition<RoutingApplicationCall>()

        override val key: AttributeKey<Routing> = AttributeKey("Routing")

        override fun install(pipeline: Application, configure: Routing.() -> Unit): Routing {
            val routing = Routing(pipeline).apply(configure)
            pipeline.intercept(ApplicationCallPipeline.Call) { routing.interceptor(this) }
            return routing
        }
    }

    private object RootRouteSelector : RouteSelector(RouteSelectorEvaluation.qualityConstant) {
        override fun evaluate(context: RoutingResolveContext, index: Int): RouteSelectorEvaluation {
            throw UnsupportedOperationException("Root selector should not be evaluated")
        }

        override fun toString(): String = ""
    }
}

val Route.application: Application
    get() = when {
        this is Routing -> application
        else -> parent?.application ?: throw UnsupportedOperationException("Cannot retrieve application from unattached routing entry")
    }

fun Application.routing(configure: Routing.() -> Unit) = featureOrNull(Routing)?.apply(configure) ?: install(Routing, configure)

