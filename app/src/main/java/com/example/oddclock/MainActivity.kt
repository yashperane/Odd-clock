package com.example.oddclock

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            text = "Long-press home screen > Widgets > Odd Clock"
            textSize = 18f
            setPadding(48, 96, 48, 48)
        })
    }
}
