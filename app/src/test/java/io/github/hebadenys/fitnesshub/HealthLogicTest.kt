package io.github.hebadenys.fitnesshub
import org.junit.Assert.*
import org.junit.Test
class HealthLogicTest { @Test fun average_isDeterministic(){ assertEquals(102.0,listOf(100.0,102.0,104.0).average(),0.001) }; @Test fun missingMetric_staysMissing(){ val weight:Double?=null;assertNull(weight) } }
