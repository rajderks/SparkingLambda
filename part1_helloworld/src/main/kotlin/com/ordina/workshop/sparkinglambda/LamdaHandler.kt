package com.ordina.workshop.sparkinglambda

import com.amazonaws.serverless.exceptions.ContainerInitializationException
import com.amazonaws.serverless.proxy.model.AwsProxyRequest
import com.amazonaws.serverless.proxy.model.AwsProxyResponse
import com.amazonaws.serverless.proxy.spark.SparkLambdaContainerHandler
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import org.apache.log4j.BasicConfigurator
import org.slf4j.LoggerFactory
import spark.Spark.*

class LambdaHandler @Throws(ContainerInitializationException::class)
constructor(): RequestHandler<AwsProxyRequest, AwsProxyResponse> {
    private val handler = SparkLambdaContainerHandler.getAwsProxyHandler()
    private var initialized = false
    private val log = LoggerFactory.getLogger(LambdaHandler::class.java)

    override fun handleRequest(req: AwsProxyRequest, ctx: Context?): AwsProxyResponse {
        BasicConfigurator.configure()
        if(!initialized) {
            defineRoutes()
            initialized = true
        }
        return handler.proxy(req, ctx)
    }

    private fun defineRoutes() {
        initExceptionHandler{ e ->
            log.error("Spark init failure", e)
            System.exit(100)
        }
        get("/hello"){ _, _ ->
            "hello world!"
        }
    }
}
