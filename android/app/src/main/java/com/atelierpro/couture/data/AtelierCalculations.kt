package com.atelierpro.couture.data

/**
 * Pure deterministic business-logic calculations for orders, haberdashery costs,
 * model profitability, and employee payroll. Tested directly by unit tests.
 */
object AtelierCalculations {

    fun calculateOrderBalance(totalPrice: Long, advancePaid: Long): Long {
        return (totalPrice - advancePaid).coerceAtLeast(0L)
    }

    fun calculateRecipeCost(
        recipe: List<RecipeEntry>,
        inventoryById: Map<String, HaberdasheryItemEntity>
    ): Long {
        return recipe.sumOf { entry ->
            val unitPrice = inventoryById[entry.itemId]?.unitPrice ?: 0L
            unitPrice * entry.quantity.coerceAtLeast(0)
        }
    }

    fun calculateModelProfitability(
        model: GarmentModelEntity,
        orders: List<OrderEntity>,
        inventoryById: Map<String, HaberdasheryItemEntity>
    ): ModelProfitability {
        val recipeCost = calculateRecipeCost(model.recipe, inventoryById)
        val modelOrders = orders.filter { it.modelId == model.id }
        val avgPrice = if (modelOrders.isNotEmpty()) {
            modelOrders.sumOf { it.totalPrice } / modelOrders.size
        } else {
            model.defaultPrice
        }
        val avgMargin = avgPrice - recipeCost
        val marginPercent = if (avgPrice > 0L) ((avgMargin * 100L) / avgPrice).toInt() else 0
        return ModelProfitability(
            orderCount = modelOrders.size,
            averageSellingPrice = avgPrice,
            haberdasheryCost = recipeCost,
            averageMargin = avgMargin,
            marginPercent = marginPercent
        )
    }

    fun calculateEmployeeEarnings(emp: EmployeeEntity, orders: List<OrderEntity>): Long {
        val assigned = orders.filter { it.assignedEmployeeId == emp.id }
        return when (emp.payMode) {
            "fixe" -> emp.payRate
            "piece" -> emp.payRate * assigned.size
            "commission" -> assigned.sumOf { (it.totalPrice * emp.payRate) / 100L }
            else -> 0L
        }
    }

    fun generateNextOrderReference(year: Int, existingOrders: List<OrderEntity>): String {
        val maxNum = existingOrders
            .mapNotNull { it.reference.substringAfterLast("-").toIntOrNull() }
            .maxOrNull() ?: 0
        return "CMD-$year-${(maxNum + 1).toString().padStart(3, '0')}"
    }
}

data class ModelProfitability(
    val orderCount: Int,
    val averageSellingPrice: Long,
    val haberdasheryCost: Long,
    val averageMargin: Long,
    val marginPercent: Int
)
