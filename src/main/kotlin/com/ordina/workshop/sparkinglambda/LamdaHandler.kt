package com.ordina.workshop.sparkinglambda

import com.amazonaws.serverless.exceptions.ContainerInitializationException
import com.amazonaws.serverless.proxy.model.AwsProxyRequest
import com.amazonaws.serverless.proxy.model.AwsProxyResponse
import com.amazonaws.serverless.proxy.spark.SparkLambdaContainerHandler
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.fasterxml.jackson.module.kotlin.*
import org.apache.log4j.BasicConfigurator
import org.slf4j.LoggerFactory
import spark.Request
import spark.Response
import spark.Spark.*


data class CalcRequestObject(val numbers:Collection<Double>)
data class CalcResponseObject(val result:Double)

internal class CalculatorLambda {
    companion object {

        internal fun sumRequest(req: Request, response: Response): String {
            val mapper = jacksonObjectMapper()
            val calcReq = mapper.readValue<CalcRequestObject>(req.body())
            if(calcReq.numbers.count() > 0) {
                val sum = calcReq.numbers.reduce({sum: Double, element:Double -> sum + element});
                return mapper.writeValueAsString(CalcResponseObject(sum))
            }
            return "Super generic error message about the numbers' count"
        }
    }
}

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
        post("/calc/sum") { req, resp -> CalculatorLambda.sumRequest(req, resp) }
    }
}
