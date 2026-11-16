package com.atelierpro.couture.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AtelierTypeConvertersTest {
    private val converters = AtelierTypeConverters()

    @Test
    fun recipeListRoundTrip() {
        val original = listOf(RecipeEntry("merc-1", 2), RecipeEntry("merc-3", 5))
        val json = converters.fromRecipeList(original)
        val restored = converters.toRecipeList(json)
        assertEquals(original, restored)
    }

    @Test
    fun measurementsMapRoundTrip() {
        val original = mapOf("Tour de poitrine" to 96.5, "Longueur robe / boubou" to 146.0)
        val json = converters.fromMeasurementsMap(original)
        val restored = converters.toMeasurementsMap(json)
        assertEquals(original, restored)
    }

    @Test
    fun stringListRoundTrip() {
        val original = listOf("Carrure", "Longueur manche", "Tour de cou")
        val json = converters.fromStringList(original)
        val restored = converters.toStringList(json)
        assertEquals(original, restored)
    }
}
