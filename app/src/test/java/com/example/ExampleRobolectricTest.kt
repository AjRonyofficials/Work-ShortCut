package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.service.FakeIdentityGenerator
import com.example.service.SmartClipboardManager
import com.example.service.TotpManager
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
  fun `verify fake identity generator`() {
    val identity = FakeIdentityGenerator.generate("US", "Male")
    assertNotNull(identity.fullName)
    assertTrue(identity.phone.isNotEmpty())
    assertTrue(identity.email.contains("@"))
  }

  @Test
  fun `verify totp generation`() {
    val code = TotpManager.generateCurrentCode("JBSWY3DPEHPK3PXP")
    assertEquals(6, code.length)
  }
}
