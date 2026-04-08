package com.netcatty.mobile

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration test that unlocks the app, adds a host, and tests SSH connection.
 * Run: adb shell am instrument -w com.netcatty.mobile/androidx.test.runner.AndroidJUnitRunner
 * Or: adb shell am instrument -w -e class com.netcatty.mobile.SshTestRunner com.netcatty.mobile/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
class SshTestRunner {

    @Test
    fun testFullFlow() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)

        // Wait for app to load
        device.waitForIdle(5000)

        // Step 1: Unlock
        // Find password field and type
        val passwordField = device.findObject(UiSelector().className("android.widget.EditText").instance(0))
        passwordField.text = "123456"

        val confirmField = device.findObject(UiSelector().className("android.widget.EditText").instance(1))
        confirmField.text = "123456"

        // Find and click Create & Unlock button
        val unlockBtn = device.findObject(UiSelector().textContains("Create & Unlock"))
        if (unlockBtn.exists()) {
            unlockBtn.click()
        } else {
            val unlockBtn2 = device.findObject(UiSelector().textContains("Unlock"))
            unlockBtn2.click()
        }

        device.waitForIdle(3000)

        // Step 2: Add host - click FAB
        val fab = device.findObject(UiSelector().descriptionContains("Add host"))
        if (fab.exists()) {
            fab.click()
        } else {
            // Try by clickable view at bottom right
            device.click(device.displayWidth - 100, device.displayHeight - 200)
        }

        device.waitForIdle(2000)

        // Fill in host form
        val fields = device.findObjects(UiSelector().className("android.widget.EditText"))
        if (fields.size >= 3) {
            fields[0].text = "TestServer"   // Label
            fields[1].text = "192.168.100.20" // Hostname
            // fields[2] might be port or username
            if (fields.size >= 5) {
                fields[2].text = "22"         // Port
                fields[3].text = "root"        // Username
                fields[4].text = "1234"        // Password
            } else if (fields.size >= 4) {
                fields[2].text = "root"
                fields[3].text = "1234"
            }
        }

        // Click Add button
        val addBtn = device.findObject(UiSelector().textContains("Add"))
        addBtn.click()

        device.waitForIdle(3000)

        // Step 3: Click SSH button on the host card
        val sshBtn = device.findObject(UiSelector().textContains("SSH"))
        if (sshBtn.exists()) {
            sshBtn.click()
        }

        device.waitForIdle(10000)

        // Check if terminal screen appeared
        val terminalText = device.findObject(UiSelector().textContains("Connecting"))
        val isConnected = terminalText.exists()

        println("TEST_RESULT: SSH connection attempt completed. Connected=$isConnected")
    }
}
