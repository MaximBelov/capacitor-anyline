package io.github.maximbelov.anyline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ScanResultsTest {
    private val barcode =
        """{"pluginID":"barcode","barcodeResult":{"barcodes":[{"value":"372123456789012","format":"CODE_128"}]},"imagePath":"/x/a.png"}"""

    @Test
    fun passesASingleResultOnAsTheObjectItIs() {
        val result = ScanResults.toCallResult(barcode, composite = false)
        val first = result.getJSONObject("barcodeResult").getJSONArray("barcodes").getJSONObject(0)
        assertEquals("372123456789012", first.getString("value"))
        assertEquals("barcode", result.getString("pluginID"))
        assertFalse(result.has("results"))
    }

    @Test
    fun putsACompositesArrayUnderResults() {
        val result = ScanResults.toCallResult("[$barcode,$barcode]", composite = true)
        assertEquals(2, result.getJSONArray("results").length())
        assertEquals("barcode", result.getJSONArray("results").getJSONObject(1).getString("pluginID"))
    }

    @Test
    fun readsAnArrayAsResultsEvenWhenTheTypeIsMissing() {
        val result = ScanResults.toCallResult("  [$barcode]", composite = false)
        assertEquals(1, result.getJSONArray("results").length())
    }

    @Test
    fun wrapsASingleObjectOfACompositeAsAListOfOne() {
        val result = ScanResults.toCallResult(barcode, composite = true)
        assertEquals(1, result.getJSONArray("results").length())
    }

    @Test(expected = org.json.JSONException::class)
    fun refusesWhatIsNotJson() {
        ScanResults.toCallResult("not json", composite = false)
    }
}
