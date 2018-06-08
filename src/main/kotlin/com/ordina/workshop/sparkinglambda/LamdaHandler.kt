package com.example.lambda

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

class CalculatorLambda {

    companion object {
        fun calcSum(req: Request, resp: Response): String {
            return calcFromSparkRequest(req, resp, ::summer)
        }

        fun calcSub(req: Request, resp: Response): String {
            return calcFromSparkRequest(req, resp, ::subtractor)
        }

        fun calcDiv(req: Request, resp: Response): String {
            return calcFromSparkRequest(req, resp, ::divider)
        }

        fun calcMultiply(req: Request, resp: Response): String {
            return calcFromSparkRequest(req, resp, ::multiplier)
        }

        private fun calcFromSparkRequest(req: Request, response: Response, reducer: (Double, Double)->Double): String {
            val mapper = jacksonObjectMapper()
            val calcReq = mapper.readValue<CalcRequestObject>(req.body())
            if(calcReq.numbers.count() > 0) {
                val calcResp = calcWithReducer(calcReq, reducer)
                return mapper.writeValueAsString(CalcResponseObject(calcResp))
            }
            return "Super generic error message"
        }

        fun calcWithReducer(calcReq: CalcRequestObject, reducer:(Double, Double) -> Double): Double {
            return calcReq.numbers.reduce(reducer)
        }

        fun multiplier(d1: Double, d2:Double): Double = d1*d2
        fun summer(d1: Double, d2: Double): Double = d1+d2
        fun subtractor(d1: Double, d2: Double): Double = d1-d2
        fun divider(d1: Double, d2: Double): Double = d1/d2
    }

}

class LambdaHandler @Throws(ContainerInitializationException::class)
constructor(): RequestHandler<AwsProxyRequest, AwsProxyResponse> {

    private val handler = SparkLambdaContainerHandler.getAwsProxyHandler()
    private var initalized = false
    private val log = LoggerFactory.getLogger(LambdaHandler::class.java)

    override fun handleRequest(req: AwsProxyRequest, ctx: Context?): AwsProxyResponse {
        BasicConfigurator.configure()
        if(!initalized) {
            defineRoutes()
            initalized = true
        }
        return handler.proxy(req, ctx)
    }

    fun defineRoutes() {
        initExceptionHandler{ e ->
            log.error("Spark init failure", e)
            System.exit(100)
        }
        get("/hello"){ _, _ ->
            "hello world!"
        }
        post("/calc/sum") { req, resp -> CalculatorLambda.calcSum(req, resp) }
        post("/calc/multi") { req, resp -> CalculatorLambda.calcMultiply(req, resp) }
        post("/calc/sub") { req, resp -> CalculatorLambda.calcSub(req, resp) }
        post("/calc/div") { req, resp -> CalculatorLambda.calcDiv(req, resp) }
    }
}

data class CalcRequestObject(val numbers:Collection<Double>)
data class CalcResponseObject(val result:Double)