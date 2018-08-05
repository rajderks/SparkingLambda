package com.ordina.workshop.sparkinglambda

import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.math.RoundingMode
import kotlin.test.assertEquals

class LambdaSpec: Spek({
    given("A calculator with 4 functions") {
        on("function `sum` with input 1,2,3,4") {
            val sum = arrayListOf(1.0,2.0,3.0,4.0).reduce(CalculatorLambda.Companion::summer)
            it("will output 10") {
                assertEquals(sum, 10.0, "Sum should be 10 but is $sum")
            }
        }
        on("function `sub` with input 1,2,3,4") {
            val sub = arrayListOf(1.0,2.0,3.0,4.0).reduce(CalculatorLambda.Companion::subtractor)
            it("will output -10") {
                assertEquals(sub, -8.0, "Sub should be -8 but is $sub")
            }
        }
        on("function `multi` with input 1,2,3,4") {
            val multi = arrayListOf(1.0,2.0,3.0,4.0).reduce(CalculatorLambda.Companion::multiplier)
            it("will output 24") {
                assertEquals(multi, 24.0, "Multi should be 10 but is $multi")
            }
        }
        on("function `div` with input 1,2,3,4") {
            val div = arrayListOf(1.0,2.0,3.0,4.0).reduce(CalculatorLambda.Companion::divider).toBigDecimal().setScale(4, RoundingMode.HALF_UP).toDouble()
            it("will output .0417") {
                assertEquals(div, 0.0417, "Div should be 0.0417 but is $div")
            }
        }
    }
})