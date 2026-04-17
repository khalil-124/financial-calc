import com.khalil.calc.logic.*
import java.io.File

fun main() {
    val engine = LoanEngine()
    val input = LoanInput(assetPrice = 20000.0, months = 60, annualRate = 7.0, rateType = RateType.FLAT)
    val result = engine.calculate(input, false)
    val first = result.schedule[0].interestPart
    val last = result.schedule.last().interestPart
    println("First: $first, Last: $last")
}
