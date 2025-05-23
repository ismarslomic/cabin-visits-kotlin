package no.slomic.smarthytte.common

enum class PersistenceResult {
    ADDED,
    DELETED,
    UPDATED,
    NO_ACTION,
}

class PersistenceResults {
    private val items = mutableListOf<PersistenceResult>()

    fun add(result: PersistenceResult) {
        items.add(result)
    }

    val addedCount: Int get() = items.count { it == PersistenceResult.ADDED }
    val updatedCount: Int get() = items.count { it == PersistenceResult.UPDATED }
    val deletedCount: Int get() = items.count { it == PersistenceResult.DELETED }
    val noActionCount: Int get() = items.count { it == PersistenceResult.NO_ACTION }
}
