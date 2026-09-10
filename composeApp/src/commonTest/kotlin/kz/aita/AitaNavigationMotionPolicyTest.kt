package kz.aita

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AitaNavigationMotionPolicyTest {
    @Test fun sameStackDoesNotAnimateOnRecomposition() {
        assertEquals(AitaNavigationMotion.NONE, aitaStackMotion(listOf("menu", "account"), listOf("menu", "account")))
    }
    @Test fun pushingAChildMovesForward() {
        assertEquals(AitaNavigationMotion.FORWARD, aitaStackMotion(listOf("menu"), listOf("menu", "account")))
    }
    @Test fun pushingMultipleChildrenStillMovesForward() {
        assertEquals(AitaNavigationMotion.FORWARD, aitaStackMotion(listOf("menu"), listOf("menu", "stores", "edit")))
    }
    @Test fun poppingAChildMovesBack() {
        assertEquals(AitaNavigationMotion.BACK, aitaStackMotion(listOf("menu", "account"), listOf("menu")))
    }
    @Test fun poppingSeveralChildrenMovesBack() {
        assertEquals(AitaNavigationMotion.BACK, aitaStackMotion(listOf("menu", "stores", "edit"), listOf("menu")))
    }
    @Test fun sameDepthReplacementIsNotAnInventedPush() {
        assertEquals(AitaNavigationMotion.REPLACE, aitaStackMotion(listOf("menu", "account"), listOf("menu", "devices")))
    }
    @Test fun resetToAnotherRootIsNotMistakenForBack() {
        assertEquals(AitaNavigationMotion.REPLACE, aitaStackMotion(listOf("menu", "stores", "edit"), listOf("login")))
    }
    @Test fun restorationOfALongerUnrelatedStackIsNotMistakenForForward() {
        assertEquals(AitaNavigationMotion.REPLACE, aitaStackMotion(listOf("login"), listOf("menu", "stores")))
    }
    @Test fun emptyStackBoundariesAreSafe() {
        assertEquals(AitaNavigationMotion.NONE, aitaStackMotion(emptyList<String>(), emptyList()))
        assertEquals(AitaNavigationMotion.REPLACE, aitaStackMotion(emptyList(), listOf("menu")))
        assertEquals(AitaNavigationMotion.REPLACE, aitaStackMotion(listOf("menu"), emptyList()))
    }
    @Test fun explicitDuplicateScreenPushStillHasDirection() {
        assertEquals(AitaNavigationMotion.FORWARD, aitaStackMotion(listOf("menu", "edit"), listOf("menu", "edit", "edit")))
    }
    @Test fun bottomTabDirectionsFollowTheirVisibleOrder() {
        assertEquals(AitaNavigationMotion.FORWARD, aitaOrderedMotion(0, 3))
        assertEquals(AitaNavigationMotion.BACK, aitaOrderedMotion(3, 0))
        assertEquals(AitaNavigationMotion.NONE, aitaOrderedMotion(2, 2))
    }
    @Test fun destinationsOutsideTheBottomBarUseANeutralTransition() {
        assertEquals(AitaNavigationMotion.REPLACE, aitaOrderedMotion(null, 2))
        assertEquals(AitaNavigationMotion.REPLACE, aitaOrderedMotion(2, null))
        assertEquals(AitaNavigationMotion.REPLACE, aitaOrderedMotion(null, null))
    }
    @Test fun directionsMirrorInRightToLeftLayouts() {
        assertEquals(1, aitaNavigationDirectionSign(AitaNavigationMotion.FORWARD, false))
        assertEquals(-1, aitaNavigationDirectionSign(AitaNavigationMotion.FORWARD, true))
        assertEquals(-1, aitaNavigationDirectionSign(AitaNavigationMotion.BACK, false))
        assertEquals(1, aitaNavigationDirectionSign(AitaNavigationMotion.BACK, true))
    }
    @Test fun neutralAndUnchangedScenesDoNotSlideSideways() {
        for (rtl in listOf(false, true)) {
            assertEquals(0, aitaNavigationDirectionSign(AitaNavigationMotion.NONE, rtl))
            assertEquals(0, aitaNavigationDirectionSign(AitaNavigationMotion.REPLACE, rtl))
        }
    }
    @Test fun travelIsProportionalOnSmallPanes() {
        assertEquals(25, aitaNavigationTravelPx(200, 48))
        assertEquals(45, aitaNavigationTravelPx(360, 48))
    }
    @Test fun wideDesktopTravelIsCapped() {
        assertEquals(48, aitaNavigationTravelPx(3840, 48))
        assertEquals(96, aitaNavigationTravelPx(7680, 96))
        assertEquals(48, aitaNavigationTravelPx(Int.MAX_VALUE, 48))
    }
    @Test fun unknownOrInvalidDimensionsNeverProduceNegativeTravel() {
        assertEquals(0, aitaNavigationTravelPx(0, 48))
        assertEquals(0, aitaNavigationTravelPx(-1, 48))
        assertEquals(0, aitaNavigationTravelPx(400, 0))
        assertEquals(0, aitaNavigationTravelPx(400, -1))
    }
    private fun scene(cart: String = "0", stack: List<String> = listOf("cart"), index: Int? = 0, family: String = "sale") =
        AitaSceneMotionTarget(family, cart, stack, index)

    @Test fun equivalentSceneObjectsDoNotRestartMotion() {
        assertEquals(AitaNavigationMotion.NONE, aitaSceneMotion(scene(), scene(stack = listOf("cart"))))
    }
    @Test fun tabReorderingDoesNotAnimateTheAlreadySelectedPage() {
        assertEquals(AitaNavigationMotion.NONE, aitaSceneMotion(scene(index = 2), scene(index = 1)))
    }
    @Test fun cartSwitchDirectionTakesPriorityOverDifferentStackDepth() {
        assertEquals(AitaNavigationMotion.FORWARD, aitaSceneMotion(scene(stack = listOf("cart", "payment", "receipt")), scene("4", index = 4)))
    }
    @Test fun returningToAnEarlierCartIsNotMistakenForAPaymentPush() {
        assertEquals(AitaNavigationMotion.BACK, aitaSceneMotion(scene("4", index = 4), scene("0", listOf("cart", "payment"), 0)))
    }
    @Test fun paymentAndReceiptFollowTheirActualStackWithinOneCart() {
        assertEquals(AitaNavigationMotion.FORWARD, aitaSceneMotion(scene(), scene(stack = listOf("cart", "payment"))))
        assertEquals(AitaNavigationMotion.BACK, aitaSceneMotion(scene(stack = listOf("cart", "payment")), scene()))
    }
    @Test fun changingWorkspaceFamilyDoesNotPretendToBeAChildScreen() {
        assertEquals(AitaNavigationMotion.REPLACE, aitaSceneMotion(scene(), scene(family = "returns")))
    }
    @Test fun differentSelectionsWithSameOrdinalStillCrossfade() {
        assertEquals(AitaNavigationMotion.REPLACE, aitaSceneMotion(scene("a", index = 0), scene("b", index = 0)))
    }
    @Test fun rapidForwardThenBackHasNoDirectionQueue() {
        val a = scene("0", index = 0)
        val b = scene("1", index = 1)
        val c = scene("3", index = 3)
        assertEquals(AitaNavigationMotion.FORWARD, aitaSceneMotion(a, b))
        assertEquals(AitaNavigationMotion.FORWARD, aitaSceneMotion(b, c))
        assertEquals(AitaNavigationMotion.BACK, aitaSceneMotion(c, a))
    }
    @Test fun allNavigationDurationsRemainShortAndBackIsNotSlower() {
        assertTrue(AITA_NAV_ENTER_MILLIS in 160..250)
        assertTrue(AITA_NAV_BACK_MILLIS in 140..AITA_NAV_ENTER_MILLIS)
        assertTrue(AITA_NAV_EXIT_MILLIS < AITA_NAV_ENTER_MILLIS)
        assertTrue(AITA_NAV_FADE_MILLIS < AITA_NAV_ENTER_MILLIS)
        assertTrue(AITA_NAV_SIZE_MILLIS <= AITA_NAV_ENTER_MILLIS)
    }
}
