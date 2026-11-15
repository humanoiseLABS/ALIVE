package com.projectego.app.domain

import com.projectego.app.domain.GalaxyCatalog.GalaxyActionTarget
import com.projectego.app.domain.GalaxyCatalog.GalaxyMemberKind
import com.projectego.app.domain.GalaxyCatalog.GalaxyStateKind
import com.projectego.app.model.FocusActivityType
import com.projectego.app.model.QuestCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GalaxyCatalogTest {

    @Test
    fun `catalog contains all 17 members in contiguous clockwise order`() {
        assertEquals(17, GalaxyCatalog.members.size)
        assertEquals((0..16).toList(), GalaxyCatalog.members.map { it.index })
        assertEquals(17, GalaxyCatalog.members.map { it.id }.distinct().size)

        val work = GalaxyCatalog.members.take(11)
        val states = GalaxyCatalog.members.drop(11)
        assertTrue(work.all { it.kind == GalaxyMemberKind.WORK })
        assertTrue(states.all { it.kind == GalaxyMemberKind.DAY_STATE })
        assertEquals(GalaxyStateKind.values().toSet(), states.mapNotNull { it.stateKind }.toSet())
    }

    @Test
    fun `creative is the default day-1 starter companion`() {
        val defaultCompanion = GalaxyCatalog.defaultCompanion()
        assertEquals("creative", defaultCompanion.id)
        assertEquals(GalaxyMemberKind.WORK, defaultCompanion.kind)
        assertNotNull(GalaxyCatalog.findById("creative"))
        assertNull(GalaxyCatalog.findById("non_existent_member"))
    }

    @Test
    fun `every quest category and focus activity resolves cleanly to a work member`() {
        QuestCategory.values().forEach { category ->
            val member = GalaxyCatalog.forQuestCategory(category)
            assertEquals(GalaxyMemberKind.WORK, member.kind)
            assertEquals(category, member.questCategory)
        }

        FocusActivityType.values().forEach { activityType ->
            val member = GalaxyCatalog.forFocusActivity(activityType)
            assertEquals(GalaxyMemberKind.WORK, member.kind)
            assertEquals(activityType, member.focusActivity)
            assertEquals(GalaxyActionTarget.Focus(activityType), member.primaryAction)
        }
    }
}
