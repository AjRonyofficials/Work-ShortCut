package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.util.Gender
import com.example.util.NameGenerator
import com.example.util.TotpHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Work ShortCut", appName)
  }

  @Test
  fun `verify name generator`() {
    val name = NameGenerator.generateName("US", Gender.MALE)
    assertNotNull(name)
    assertTrue(name.isNotBlank())
  }

  @Test
  fun `verify totp generation`() {
    val result = TotpHelper.generateTotp("JBSWY3DPEHPK3PXP")
    assertNotNull(result)
    assertEquals(6, result?.code?.length)
  }

  @Test
  fun `test virtual number checker live`() {
    val res1 = com.example.service.VirtualNumberChecker.checkSingleNumberDirect("+18005550199")
    assertEquals(com.example.service.PhoneItem.TagType.TAKEN, res1.tagType)
    val res2 = com.example.service.VirtualNumberChecker.checkSingleNumberDirect("+198765432109876")
    assertEquals(com.example.service.PhoneItem.TagType.FRESH, res2.tagType)
  }
}
