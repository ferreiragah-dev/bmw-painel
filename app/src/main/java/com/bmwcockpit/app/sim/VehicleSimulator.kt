package com.bmwcockpit.app.sim

import com.bmwcockpit.app.data.model.DataOrigin
import com.bmwcockpit.app.data.model.Reading
import com.bmwcockpit.app.data.model.SensorId
import com.bmwcockpit.app.data.telemetry.PowerEstimator
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Longitudinal physics model of a BMW 320i F30 (N20 2.0 turbo, Stage 2 map, ZF 8HP), used ONLY by the demo
 * mode. Speed, RPM, gear, boost, load and air flow are all derived from one consistent state (throttle →
 * boost → torque → wheel force → speed → RPM through the selected gear), so the sensors never jump
 * independently. Temperatures and voltage follow first-order dynamics driven by load.
 *
 * Every value produced here is tagged [DataOrigin.SIMULATED].
 */
class VehicleSimulator(seed: Int = 320) {

    enum class Phase { STOPPED, LAUNCH, ACCELERATE, CRUISE, COAST, BRAKE }

    private var rnd = Random(seed)
    private val seed0 = seed

    // Vehicle constants
    private val massKg = 1600.0
    private val dragArea = 0.29 * 2.2
    private val rollingCoef = 0.012
    private val wheelRadius = 0.316
    private val finalDrive = 3.154
    private val ratios = doubleArrayOf(4.714, 3.143, 2.106, 1.667, 1.285, 1.000, 0.839, 0.667)
    private val idleRpm = 760.0
    private val limiterRpm = 6900.0
    private val displacementM3 = 0.001997
    private val ambientC = 28.0
    private val ambientKpa = 101.3

    // State
    var timeS = 0.0; private set
    var speedMs = 0.0; private set
    var rpm = idleRpm; private set
    var gear = 1; private set
    var throttle = 0.0; private set
    var brake = 0.0; private set
    var boostBar = -0.65; private set
    var torqueNm = 0.0; private set
    var coolantC = 74.0; private set
    var oilC = 66.0; private set
    var iatC = 32.0; private set
    var lambda = 1.0; private set
    var phase = Phase.STOPPED; private set

    private var phaseTimer = 2.0
    private var targetKmh = 0.0
    private var throttleTarget = 0.0
    private var brakeTarget = 1.0
    private var shiftTimer = 0.0
    private var sinceShift = 10.0
    private var forcedLaunchTo: Double? = null

    val speedKmh: Double get() = speedMs * 3.6

    fun reset() {
        rnd = Random(seed0)
        timeS = 0.0; speedMs = 0.0; rpm = idleRpm; gear = 1; throttle = 0.0; brake = 1.0
        boostBar = -0.65; torqueNm = 0.0; coolantC = 74.0; oilC = 66.0; iatC = 32.0; lambda = 1.0
        phase = Phase.STOPPED; phaseTimer = 2.0; targetKmh = 0.0; throttleTarget = 0.0; brakeTarget = 1.0
        shiftTimer = 0.0; sinceShift = 10.0; forcedLaunchTo = null
    }

    /**
     * Brakes to a full stop, waits ~2 s and then launches at full throttle up to [toKmh].
     * Used to exercise the 0–100 km/h and quarter mile measurements.
     */
    fun launchFromStandstill(toKmh: Double = 210.0) {
        forcedLaunchTo = toKmh
        if (speedMs < 0.3) enter(Phase.STOPPED, 2.0) else {
            targetKmh = 0.0
            enter(Phase.BRAKE, 30.0)
            brakeTarget = 0.8
        }
    }

    fun step(dtS: Double) {
        var remaining = dtS
        while (remaining > 1e-6) {
            val h = min(0.01, remaining)
            integrate(h)
            remaining -= h
        }
    }

    // ---------------------------------------------------------------- driver

    private fun enter(p: Phase, duration: Double) {
        phase = p
        phaseTimer = duration
    }

    private fun nextPhase() {
        val kmh = speedKmh
        forcedLaunchTo?.let {
            if (phase == Phase.STOPPED) {
                targetKmh = it
                enter(Phase.LAUNCH, 60.0)
                forcedLaunchTo = null
                return
            }
        }
        when (phase) {
            Phase.STOPPED -> {
                if (rnd.nextFloat() < 0.45) {
                    targetKmh = 120.0 + rnd.nextDouble() * 70.0
                    enter(Phase.LAUNCH, 40.0)
                } else {
                    targetKmh = 45.0 + rnd.nextDouble() * 50.0
                    throttleTarget = 0.35 + rnd.nextDouble() * 0.3
                    enter(Phase.ACCELERATE, 40.0)
                }
            }
            Phase.LAUNCH, Phase.ACCELERATE -> enter(Phase.CRUISE, 6.0 + rnd.nextDouble() * 8.0)
            Phase.CRUISE -> {
                val r = rnd.nextFloat()
                when {
                    r < 0.30 -> enter(Phase.COAST, 2.5 + rnd.nextDouble() * 3.0)
                    r < 0.60 && kmh < 150 -> {
                        targetKmh = min(220.0, kmh + 30.0 + rnd.nextDouble() * 60.0)
                        throttleTarget = 0.55 + rnd.nextDouble() * 0.45
                        enter(Phase.ACCELERATE, 30.0)
                    }
                    else -> {
                        targetKmh = if (rnd.nextFloat() < 0.4) 0.0 else 30.0 + rnd.nextDouble() * 40.0
                        brakeTarget = 0.25 + rnd.nextDouble() * 0.4
                        enter(Phase.BRAKE, 30.0)
                    }
                }
            }
            Phase.COAST -> {
                targetKmh = if (rnd.nextFloat() < 0.35) 0.0 else max(25.0, kmh - 40.0)
                brakeTarget = 0.3 + rnd.nextDouble() * 0.3
                enter(Phase.BRAKE, 30.0)
            }
            Phase.BRAKE -> {
                if (kmh < 1.0) enter(Phase.STOPPED, 2.0 + rnd.nextDouble() * 2.5)
                else {
                    targetKmh = kmh + 20.0 + rnd.nextDouble() * 40.0
                    throttleTarget = 0.4 + rnd.nextDouble() * 0.4
                    enter(Phase.ACCELERATE, 30.0)
                }
            }
        }
    }

    private fun drive(h: Double) {
        phaseTimer -= h
        val kmh = speedKmh
        when (phase) {
            Phase.STOPPED -> { throttleTargetSet(0.0); brakeTargetSet(1.0) }
            Phase.LAUNCH -> {
                throttleTargetSet(1.0); brakeTargetSet(0.0)
                if (kmh >= targetKmh) phaseTimer = 0.0
            }
            Phase.ACCELERATE -> {
                throttleTargetSet(throttleTarget); brakeTargetSet(0.0)
                if (kmh >= targetKmh) phaseTimer = 0.0
            }
            Phase.CRUISE -> {
                // Proportional speed hold: road-load throttle plus correction.
                val err = targetKmh - kmh
                val base = 0.10 + kmh / 900.0
                throttleTargetSet((base + err * 0.03).coerceIn(0.0, 0.7)); brakeTargetSet(0.0)
            }
            Phase.COAST -> { throttleTargetSet(0.0); brakeTargetSet(0.0) }
            Phase.BRAKE -> {
                throttleTargetSet(0.0); brakeTargetSet(brakeTarget)
                if (kmh <= targetKmh + 0.5 || kmh < 0.5) phaseTimer = 0.0
            }
        }
        if (phaseTimer <= 0.0) nextPhase()
        // Pedal dynamics: a human foot does not move instantly.
        throttle += (curThrottleTarget - throttle) * min(1.0, h * 6.0)
        brake += (curBrakeTarget - brake) * min(1.0, h * 5.0)
    }

    private var curThrottleTarget = 0.0
    private var curBrakeTarget = 1.0
    private fun throttleTargetSet(v: Double) { curThrottleTarget = v }
    private fun brakeTargetSet(v: Double) { curBrakeTarget = v }

    // --------------------------------------------------------------- physics

    private fun boostCurve(r: Double): Double = when {
        r < 1250 -> 0.15 + (r - idleRpm).coerceAtLeast(0.0) / 500.0 * 0.3
        r < 2400 -> 0.45 + (r - 1250) / 1150.0 * 1.0
        r < 5200 -> 1.45
        else -> 1.45 - (r - 5200) / 1700.0 * 0.4
    }

    private fun ve(r: Double): Double = (0.82 + 0.13 * sin(PI * (r - 1000.0) / 6000.0)).coerceIn(0.75, 0.96)

    private fun wheelDerivedRpm(g: Int): Double =
        speedMs / wheelRadius / (2 * PI) * 60.0 * ratios[g - 1] * finalDrive

    private fun integrate(h: Double) {
        timeS += h
        drive(h)
        sinceShift += h

        // Automatic gearbox
        if (shiftTimer > 0) shiftTimer -= h
        if (speedMs < 1.5) {
            if (gear != 1) { gear = 1; shiftTimer = 0.18; sinceShift = 0.0 }
        } else if (shiftTimer <= 0 && sinceShift > 0.6) {
            val up = 1800.0 + throttle.pow(1.2) * 4800.0
            val down = 1100.0 + throttle * 1500.0
            val wr = wheelDerivedRpm(gear)
            if (wr > up && gear < 8) { gear++; shiftTimer = 0.18; sinceShift = 0.0 }
            else if (wr < down && gear > 1) { gear--; shiftTimer = 0.18; sinceShift = 0.0 }
            else if (throttle > 0.85 && gear > 1 && wheelDerivedRpm(gear - 1) < 6000.0 && wr < 3800.0) {
                gear--; shiftTimer = 0.2; sinceShift = 0.0 // kickdown
            }
        }

        // Engine speed: torque converter slip at low speed, locked otherwise.
        val wheelRpm = wheelDerivedRpm(gear)
        val slipFactor = 1.0 - (wheelRpm / 2200.0).coerceIn(0.0, 1.0)
        val converterRpm = idleRpm + throttle * 1900.0 * slipFactor
        val targetRpm = max(wheelRpm, max(idleRpm, converterRpm)).coerceAtMost(limiterRpm + 50)
        rpm += (targetRpm - rpm) * min(1.0, h * 14.0)

        // Boost: first-order spool dynamics towards a throttle/RPM dependent target.
        val target = -0.65 * (1.0 - throttle).pow(1.5) + throttle.pow(1.4) * boostCurve(rpm)
        val tau = if (target > boostBar) 0.38 else 0.15
        boostBar += (target - boostBar) * min(1.0, h / tau)
        val mapKpa = ambientKpa + boostBar * 100.0

        // Engine torque
        val friction = 18.0 + rpm * 0.0035
        torqueNm = when {
            rpm >= limiterRpm -> -friction
            throttle < 0.02 && rpm > 1100 -> -(15.0 + rpm * 0.005) // overrun fuel cut
            else -> 190.0 * (mapKpa / ambientKpa) * ve(rpm) - friction
        }
        val converterMult = 1.0 + 0.8 * slipFactor * throttle
        val shiftCut = if (shiftTimer > 0) 0.35 else 1.0

        // Longitudinal dynamics
        val overall = ratios[gear - 1] * finalDrive
        val wheelForce = torqueNm * converterMult * shiftCut * overall * 0.9 / wheelRadius
        val drag = 0.5 * 1.2 * dragArea * speedMs * speedMs
        val roll = if (speedMs > 0.05) massKg * 9.81 * rollingCoef else 0.0
        val brakeForce = brake * 0.9 * massKg * 9.81
        val mEff = massKg * (1.04 + 0.0012 * overall * overall)
        var net = wheelForce - drag - roll - brakeForce
        if (speedMs <= 0.01 && net < 0) net = 0.0
        speedMs = max(0.0, speedMs + net / mEff * h)

        // Mixture
        val lambdaTarget = if (throttle > 0.85 && boostBar > 0.8) 0.82 else 1.0
        lambda += (lambdaTarget - lambda) * min(1.0, h * 3.0)

        // Thermal: first-order approach to load dependent targets.
        val load = loadPct() / 100.0
        val coolantTarget = 88.0 + load * 12.0 + (ambientC - 20.0) * 0.2
        coolantC += (coolantTarget - coolantC) * min(1.0, h / 40.0)
        val oilTarget = 92.0 + load * 22.0 + rpm / 7000.0 * 10.0
        oilC += (oilTarget - oilC) * min(1.0, h / 90.0)
        val soak = if (speedMs < 4.0) 12.0 else 0.0
        val iatTarget = ambientC + max(0.0, boostBar) * 9.0 + soak
        iatC += (iatTarget - iatC) * min(1.0, h / 8.0)
    }

    private fun loadPct(): Double = (max(18.0, torqueNm / 4.05)).coerceIn(0.0, 100.0)

    private fun mafGs(): Double {
        val mapKpa = ambientKpa + boostBar * 100.0
        val rho = mapKpa * 1000.0 / (287.0 * (iatC + 273.15))
        return rho * displacementM3 * rpm / 120.0 * ve(rpm) * 1000.0
    }

    private fun moduleVoltage(): Double = 14.32 - loadPct() * 0.003 + 0.03 * sin(timeS * 0.7)

    /** Snapshot of every simulated sensor, all tagged SIMULATED. */
    fun readings(ts: Long): List<Reading> {
        val s = DataOrigin.SIMULATED
        fun r(id: SensorId, v: Double) = Reading.of(id, v.toFloat(), ts, s)
        val maf = r(SensorId.MAF, mafGs())
        val rpmR = r(SensorId.RPM, rpm)
        val lambdaR = r(SensorId.LAMBDA, lambda)
        val loadR = r(SensorId.ENGINE_LOAD, loadPct())
        val oilPressure = (1.1 + rpm / 1000.0 * 0.78 - (oilC - 90.0) * 0.01).coerceIn(0.8, 5.4)
        return listOf(
            r(SensorId.SPEED, speedKmh),
            rpmR,
            r(SensorId.GEAR, gear.toDouble()),
            r(SensorId.THROTTLE, throttle * 100.0),
            loadR,
            r(SensorId.MAP, ambientKpa + boostBar * 100.0),
            r(SensorId.BARO, ambientKpa),
            r(SensorId.BOOST, boostBar),
            maf,
            lambdaR,
            r(SensorId.COOLANT_TEMP, coolantC),
            r(SensorId.OIL_TEMP, oilC),
            r(SensorId.IAT, iatC),
            r(SensorId.OIL_PRESSURE, oilPressure),
            r(SensorId.MODULE_VOLTAGE, moduleVoltage()),
            r(SensorId.ADAPTER_VOLTAGE, moduleVoltage() - 0.12),
        ) + PowerEstimator.readings(maf, rpmR, lambdaR, loadR, ts)
    }

    /** Sanity value used by tests: speed implied by the current gear and RPM when the converter is locked. */
    fun impliedSpeedKmh(): Double =
        rpm / 60.0 * 2 * PI * wheelRadius / (ratios[gear - 1] * finalDrive) * 3.6

    fun isConverterLocked(): Boolean = abs(wheelDerivedRpm(gear) - rpm) < rpm * 0.03 && speedKmh > 25 && shiftTimer <= 0
}
