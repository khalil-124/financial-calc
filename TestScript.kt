fun main() {
    val score = 100 - 40 - 25 - 0 // score is 35 ? Wait, dti is 65% > 50, so score -= 40. emergency = 0 < 1 so score -= 25. disposable = 1000 - 450 - 200 = 350. so no penalty. score = 35. RiskLevel.HIGH. The test expects RiskLevel.CRITICAL and score < 40. But score is 35 which is >= 25, so RiskLevel is HIGH, not CRITICAL.
}
