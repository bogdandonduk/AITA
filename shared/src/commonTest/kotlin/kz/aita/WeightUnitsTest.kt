package kz.aita

import kotlin.test.*

class WeightUnitsTest {
    private fun label(plu: String, grams: Int): String {
        val body="22"+plu.padStart(5,'0')+grams.toString().padStart(5,'0')
        val sum=body.reversed().mapIndexed { i,c -> c.digitToInt() * if (i%2==0) 3 else 1 }.sum()
        return body+((10-sum%10)%10)
    }
    @Test fun differentWeightsShareOneProductIdentityWithoutSavingWeightDigits() {
        val a=label("12345",250);val b=label("12345",1275)
        assertEquals(listOf("2212345"),a.toStoredGoodsItemBarcodeCandidates(weightEncoded=true))
        assertEquals(a.toStoredGoodsItemBarcode(true),b.toStoredGoodsItemBarcode(true))
        assertTrue(storedBarcodeMatchesScannedTransactionBarcode("2212345",b,true))
        assertFalse(storedBarcodeMatchesScannedTransactionBarcode("2212346",b,true))
        assertEquals(250,a.parseEmbeddedWeightBarcode()!!.weightGrams)
    }
    @Test fun damagedChecksumsNeverInjectAnAutomaticWeight() {
        val valid=label("12345",250)
        val broken=valid.dropLast(1)+((valid.last().digitToInt()+1)%10)
        assertTrue(broken.parseEmbeddedWeightBarcodeFormats().isEmpty())
        assertEquals(listOf(broken),broken.toStoredGoodsItemBarcodeCandidates())
        assertNull(label("12345",0).parseEmbeddedWeightBarcode())
    }
    @Test fun scaleGramsRespectTheItemsPricingUnit() {
        val barcode=label("12345",250).parseEmbeddedWeightBarcode()!!
        val kg=QuantityDataModel("1",listOf(LocalizedStringDataModel("en","kg")),roundTotal=false,pricedAmount=1.0)
        assertEquals(.250,kg.quantityFromScale(barcode)!!.total)
        assertEquals(250.0,gramsQuantityUnit().quantityFromScale(barcode)!!.total)
        val pieces=kg.copy(id="0",immutableUnitName=listOf(LocalizedStringDataModel("en","pc")),roundTotal=true)
        assertNull(pieces.quantityFromScale(barcode))
        assertEquals(listOf(kg,gramsQuantityUnit()),listOf(kg).withGramUnit().withGramUnit())
    }
    @Test fun normalProductEanStaysIntact() {
        assertEquals(listOf("4006381333931"),"4006381333931".toStoredGoodsItemBarcodeCandidates())
        assertTrue("4006381333931".parseEmbeddedWeightBarcodeFormats().isEmpty())
    }
    @Test fun pieceGoodsWithInternalRetailPrefixNeverLoseTheirFullIdentity() {
        val first=label("12345",250);val second=label("12345",1275)
        val piece=GoodsItemDataModel(barcodes=listOf(first),measurementUnitId="0")
        assertEquals(listOf(first),piece.allBarcodeValues())
        assertEquals(listOf(first),first.toStoredGoodsItemBarcodeCandidates())
        assertFalse(storedBarcodeMatchesScannedTransactionBarcode(first,second))
        assertFalse(piece.matchesEmbeddedWeightBarcode(second.parseEmbeddedWeightBarcode()!!))
        val weight=piece.copy(measurementUnitId="1")
        assertEquals(listOf("2212345"),weight.allBarcodeValues())
        assertTrue(weight.matchesEmbeddedWeightBarcode(second.parseEmbeddedWeightBarcode()!!))
    }
}
